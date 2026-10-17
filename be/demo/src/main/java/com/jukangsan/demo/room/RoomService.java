package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.ChatMessage;
import com.jukangsan.demo.room.RoomDtos.PlayerView;
import com.jukangsan.demo.room.RoomDtos.RoomView;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RoomService {
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRTUVWXYZ2346789";
	private static final int ROOM_CODE_LENGTH = 5;
	private static final int PASSWORD_ITERATIONS = 120_000;
	private static final int PASSWORD_KEY_LENGTH = 256;

	private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
	private final Map<String, PlayerSession> sessions = new ConcurrentHashMap<>();
	private final Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();
	private final Map<String, java.util.Set<String>> activeConnections = new ConcurrentHashMap<>();
	private final Map<String, UUID> disconnectTokens = new ConcurrentHashMap<>();
	private final Map<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();
	private final SecureRandom secureRandom = new SecureRandom();
	private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;
	private final RoomConfig defaultRoomConfig;
	private final ScheduledExecutorService disconnectScheduler =
			Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "room-disconnect-timeouts");
				thread.setDaemon(true);
				return thread;
			});
	private final long disconnectGraceSeconds;

	public RoomService(
			ObjectProvider<SimpMessagingTemplate> messagingTemplate,
			@Value("${game.room.default-round-count:7}") int defaultRoundCount,
			@Value("${game.room.default-coin-price:50}") int defaultCoinPrice,
			@Value("${game.room.default-turn-duration-seconds:120}") int defaultTurnDurationSeconds,
			@Value("${game.room.disconnect-grace-seconds:30}") long disconnectGraceSeconds
	) {
		this.messagingTemplate = messagingTemplate;
		this.defaultRoomConfig = new RoomConfig(
				defaultRoundCount, defaultCoinPrice, defaultTurnDurationSeconds);
		if (disconnectGraceSeconds < 1) {
			throw new IllegalArgumentException("연결 종료 유예 시간은 1초 이상이어야 합니다.");
		}
		this.disconnectGraceSeconds = disconnectGraceSeconds;
	}

	public String createRoom(String password) {
		String salt = "";
		String hash = "";
		if (password != null && !password.isBlank()) {
			byte[] saltBytes = new byte[16];
			secureRandom.nextBytes(saltBytes);
			salt = Base64.getEncoder().encodeToString(saltBytes);
			hash = hashPassword(password, saltBytes);
		}

		for (int attempt = 0; attempt < 10; attempt++) {
			String code = generateRoomCode();
			if (rooms.putIfAbsent(code, new GameRoom(code, salt, hash, defaultRoomConfig)) == null) {
				return code;
			}
		}
		throw new RoomException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_CODE_UNAVAILABLE",
				"새 방 코드를 만들지 못했습니다. 다시 시도해 주세요.");
	}

	public RoomDtos.JoinRoomResponse joinRoom(String code, String nickname, String password) {
		GameRoom room = getRoom(code);
		String cleanNickname = nickname == null ? "" : nickname.strip();
		if (cleanNickname.isBlank() || cleanNickname.codePointCount(0, cleanNickname.length()) > 20) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 1~20자여야 합니다.");
		}

		synchronized (room) {
			verifyPassword(room, password);
			if (!"WAITING".equals(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "ROOM_ALREADY_STARTED", "이미 시작된 방에는 입장할 수 없습니다.");
			}
			if (room.players.size() >= GameRoom.MAX_PLAYERS) {
				throw new RoomException(HttpStatus.CONFLICT, "ROOM_FULL", "방 인원이 가득 찼습니다.");
			}
			String normalizedNickname = normalizeNickname(cleanNickname);
			boolean duplicate = room.players.values().stream()
					.anyMatch(player -> normalizeNickname(player.nickname).equals(normalizedNickname));
			if (duplicate) {
				throw new RoomException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
			}

			String playerId = UUID.randomUUID().toString();
			String token = generateSessionToken();
			GameRoom.Player player = new GameRoom.Player(playerId, cleanNickname);
			room.players.put(playerId, player);
			if (room.hostPlayerId == null) {
				room.hostPlayerId = playerId;
			}
			sessions.put(token, new PlayerSession(token, room.code, playerId));
			playerSessions.put(playerId, new PlayerSession(token, room.code, playerId));
			broadcastRoom(room);
			return new RoomDtos.JoinRoomResponse(room.code, player.nickname, token);
		}
	}

	public RoomView getPublicRoom(String code) {
		GameRoom room = getRoom(code);
		synchronized (room) {
			return toRoomView(room);
		}
	}

	public PlayerSession authenticate(String token) {
		PlayerSession session = sessions.get(token);
		if (session == null) {
			throw new RoomException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		GameRoom room = getRoom(session.roomCode());
		synchronized (room) {
			if (!room.players.containsKey(session.playerId())) {
				throw new RoomException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "유효하지 않은 세션입니다.");
			}
		}
		return session;
	}

	public PlayerSession authenticateByPlayerId(String playerId) {
		PlayerSession session = playerSessions.get(playerId);
		if (session == null) {
			throw new RoomException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		return authenticate(session.token());
	}

	public GameRoom.Player requirePlayer(PlayerSession session, String roomCode) {
		if (!session.roomCode().equals(normalizeRoomCode(roomCode))) {
			throw new RoomException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "이 방에 접근할 권한이 없습니다.");
		}
		GameRoom room = getRoom(session.roomCode());
		synchronized (room) {
			GameRoom.Player player = room.players.get(session.playerId());
			if (player == null) {
				throw new RoomException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "이 방에 접근할 권한이 없습니다.");
			}
			return player;
		}
	}

	public void sendSnapshot(PlayerSession session, String roomCode) {
		GameRoom room = getRoom(normalizeRoomCode(roomCode));
		synchronized (room) {
			GameRoom.Player player = requirePlayer(session, room.code);
			messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
					RoomDtos.RoomEvent.of("snapshot",
							new RoomDtos.PrivateSnapshot(toRoomView(room), player.nickname)));
		}
	}

	public void setReady(PlayerSession session, String roomCode, boolean ready) {
		GameRoom room = getMemberRoom(session, roomCode);
		synchronized (room) {
			requireWaiting(room);
			room.players.get(session.playerId()).ready = ready;
			broadcastRoom(room);
		}
	}

	public void updateSettings(PlayerSession session, String roomCode, RoomConfig config) {
		GameRoom room = getMemberRoom(session, roomCode);
		synchronized (room) {
			requireHost(room, session);
			requireWaiting(room);
			room.config = config;
			broadcastRoom(room);
		}
	}

	public void startGame(PlayerSession session, String roomCode) {
		GameRoom room = getMemberRoom(session, roomCode);
		synchronized (room) {
			requireHost(room, session);
			requireWaiting(room);
			if (room.players.size() < 4) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS", "게임 시작에는 4명 이상이 필요합니다.");
			}
			room.phase = "PREPARE";
			broadcastRoom(room);
		}
	}

	public void sendChat(PlayerSession session, String roomCode, String message) {
		GameRoom room = getMemberRoom(session, roomCode);
		String cleanMessage = message == null ? "" : message.strip();
		if (cleanMessage.isBlank() || cleanMessage.codePointCount(0, cleanMessage.length()) > 500) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_MESSAGE", "메시지는 1~500자여야 합니다.");
		}
		synchronized (room) {
			GameRoom.Player player = requirePlayer(session, room.code);
			ChatMessage chatMessage = new ChatMessage(player.nickname, cleanMessage, Instant.now());
			messagingTemplate.getObject().convertAndSend(topic(room.code),
					RoomDtos.RoomEvent.of("chat", chatMessage));
		}
	}

	public void assertRoomSubscription(PlayerSession session, String roomCode) {
		requirePlayer(session, roomCode);
	}

	public void connected(PlayerSession session, String websocketSessionId) {
		if (websocketSessionId == null || websocketSessionId.isBlank()) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_WEBSOCKET_SESSION",
					"웹소켓 세션을 확인할 수 없습니다.");
		}
		GameRoom room = getRoom(session.roomCode());
		synchronized (room) {
			requirePlayer(session, room.code);
			cancelPendingDisconnect(session.playerId());
			activeConnections.computeIfAbsent(session.playerId(), ignored -> ConcurrentHashMap.newKeySet())
					.add(websocketSessionId);
		}
	}

	public void disconnect(String playerId, String websocketSessionId) {
		PlayerSession session = playerSessions.get(playerId);
		if (session == null) {
			return;
		}
		GameRoom room = rooms.get(session.roomCode());
		if (room == null) {
			return;
		}
		synchronized (room) {
			java.util.Set<String> connections = activeConnections.get(playerId);
			if (connections == null || !connections.remove(websocketSessionId) || !connections.isEmpty()) {
				return;
			}
			activeConnections.remove(playerId, connections);
			scheduleDisconnect(room, playerId);
		}
	}

	private GameRoom getMemberRoom(PlayerSession session, String roomCode) {
		requirePlayer(session, roomCode);
		return getRoom(session.roomCode());
	}

	private void scheduleDisconnect(GameRoom room, String playerId) {
		UUID token = UUID.randomUUID();
		disconnectTokens.put(playerId, token);
		ScheduledFuture<?> future = disconnectScheduler.schedule(
				() -> removeDisconnectedPlayer(room, playerId, token),
				disconnectGraceSeconds, TimeUnit.SECONDS);
		pendingDisconnects.put(playerId, future);
	}

	private void removeDisconnectedPlayer(GameRoom room, String playerId, UUID token) {
		synchronized (room) {
			if (!disconnectTokens.remove(playerId, token)) {
				return;
			}
			pendingDisconnects.remove(playerId);
			if (activeConnections.containsKey(playerId) || room.players.remove(playerId) == null) {
				return;
			}
			sessions.values().removeIf(session -> session.playerId().equals(playerId));
			playerSessions.remove(playerId);
			activeConnections.remove(playerId);
			if (playerId.equals(room.hostPlayerId)) {
				room.hostPlayerId = room.players.keySet().stream().findFirst().orElse(null);
			}
			if (room.players.isEmpty()) {
				rooms.remove(room.code, room);
			} else {
				broadcastRoom(room);
			}
		}
	}

	private void cancelPendingDisconnect(String playerId) {
		disconnectTokens.remove(playerId);
		ScheduledFuture<?> future = pendingDisconnects.remove(playerId);
		if (future != null) {
			future.cancel(false);
		}
	}

	@PreDestroy
	void stopDisconnectScheduler() {
		disconnectScheduler.shutdownNow();
	}

	private void verifyPassword(GameRoom room, String password) {
		if (room.passwordHash.isEmpty()) {
			return;
		}
		byte[] salt = Base64.getDecoder().decode(room.passwordSalt);
		byte[] actual = Base64.getDecoder().decode(hashPassword(password == null ? "" : password, salt));
		byte[] expected = Base64.getDecoder().decode(room.passwordHash);
		if (!MessageDigest.isEqual(expected, actual)) {
			throw new RoomException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "방 비밀번호가 올바르지 않습니다.");
		}
	}

	private String hashPassword(String password, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
		try {
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			return Base64.getEncoder().encodeToString(hash);
		} catch (Exception exception) {
			throw new IllegalStateException("방 비밀번호를 안전하게 처리하지 못했습니다.", exception);
		} finally {
			spec.clearPassword();
		}
	}

	private GameRoom getRoom(String code) {
		String normalizedCode = normalizeRoomCode(code);
		GameRoom room = rooms.get(normalizedCode);
		if (room == null) {
			throw new RoomException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
		}
		return room;
	}

	private String normalizeRoomCode(String code) {
		if (code == null || !code.matches("(?i)[A-HJ-NP-RT-Z2-9]{4,6}")) {
			throw new RoomException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
		}
		return code.toUpperCase(Locale.ROOT);
	}

	private void requireWaiting(GameRoom room) {
		if (!"WAITING".equals(room.phase)) {
			throw new RoomException(HttpStatus.CONFLICT, "ROOM_NOT_WAITING", "대기 중인 방에서만 사용할 수 있습니다.");
		}
	}

	private void requireHost(GameRoom room, PlayerSession session) {
		requirePlayer(session, room.code);
		if (!session.playerId().equals(room.hostPlayerId)) {
			throw new RoomException(HttpStatus.FORBIDDEN, "HOST_ONLY", "방장만 실행할 수 있습니다.");
		}
	}

	private RoomView toRoomView(GameRoom room) {
		List<PlayerView> players = new ArrayList<>();
		for (GameRoom.Player player : room.players.values()) {
			players.add(new PlayerView(player.nickname,
					player.id.equals(room.hostPlayerId), player.ready));
		}
		return new RoomView(room.code, room.phase, List.copyOf(players), room.config);
	}

	private void broadcastRoom(GameRoom room) {
		messagingTemplate.getObject().convertAndSend(topic(room.code),
				RoomDtos.RoomEvent.of("room", toRoomView(room)));
	}

	private String topic(String roomCode) {
		return "/topic/room/" + roomCode;
	}

	private String generateRoomCode() {
		StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
		for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
			code.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

	private String generateSessionToken() {
		byte[] token = new byte[32];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	private String normalizeNickname(String nickname) {
		return Normalizer.normalize(nickname.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	public record PlayerSession(String token, String roomCode, String playerId) {
	}
}
