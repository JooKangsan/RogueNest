package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.ChatMessage;
import com.jukangsan.demo.room.RoomDtos.PlayerView;
import com.jukangsan.demo.room.RoomDtos.RoomSummary;
import com.jukangsan.demo.room.RoomDtos.RoomView;
import com.jukangsan.demo.room.RoomDtos.WalletView;
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
import java.util.Collections;
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
	private final ScheduledExecutorService phaseScheduler =
			Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "room-phase-timers");
				thread.setDaemon(true);
				return thread;
			});
	private final long disconnectGraceSeconds;
	private final int backgroundCount;
	private final int minPlayers;
	private final int startGold;
	private final int maxTurns;
	private final long roleRevealSeconds;
	private final long roundStartSeconds;
	private final double murdererChance;

	public RoomService(
			ObjectProvider<SimpMessagingTemplate> messagingTemplate,
			@Value("${game.room.default-round-count:7}") int defaultRoundCount,
			@Value("${game.room.default-coin-price:50}") int defaultCoinPrice,
			@Value("${game.room.default-turn-duration-seconds:120}") int defaultTurnDurationSeconds,
			@Value("${game.room.disconnect-grace-seconds:30}") long disconnectGraceSeconds,
			@Value("${game.room.background-count:20}") int backgroundCount,
			@Value("${game.room.min-players:4}") int minPlayers,
			@Value("${game.economy.start-gold:5}") int startGold,
			@Value("${game.round.max-turns:7}") int maxTurns,
			@Value("${game.round.role-reveal-seconds:5}") long roleRevealSeconds,
			@Value("${game.round.start-announce-seconds:3}") long roundStartSeconds,
			@Value("${game.role.murderer-chance:0.3}") double murdererChance
	) {
		this.messagingTemplate = messagingTemplate;
		this.defaultRoomConfig = new RoomConfig(
				defaultRoundCount, defaultCoinPrice, defaultTurnDurationSeconds);
		if (disconnectGraceSeconds < 1) {
			throw new IllegalArgumentException("연결 종료 유예 시간은 1초 이상이어야 합니다.");
		}
		this.disconnectGraceSeconds = disconnectGraceSeconds;
		if (backgroundCount < 1) {
			throw new IllegalArgumentException("배경 이미지 개수는 1 이상이어야 합니다.");
		}
		this.backgroundCount = backgroundCount;
		if (minPlayers < 1 || minPlayers > GameRoom.MAX_PLAYERS) {
			throw new IllegalArgumentException("최소 시작 인원은 1~" + GameRoom.MAX_PLAYERS + "명이어야 합니다.");
		}
		this.minPlayers = minPlayers;
		if (startGold < 0 || maxTurns < 1) {
			throw new IllegalArgumentException("시작 골드는 0 이상, 최대 턴 수는 1 이상이어야 합니다.");
		}
		this.startGold = startGold;
		this.maxTurns = maxTurns;
		if (roleRevealSeconds < 1 || roundStartSeconds < 1) {
			throw new IllegalArgumentException("라운드 준비 시간은 1초 이상이어야 합니다.");
		}
		if (murdererChance < 0 || murdererChance > 1) {
			throw new IllegalArgumentException("살인마 등장 확률은 0~1 사이여야 합니다.");
		}
		this.roleRevealSeconds = roleRevealSeconds;
		this.roundStartSeconds = roundStartSeconds;
		this.murdererChance = murdererChance;
	}

	public String createRoom(String roomName, String password) {
		String cleanRoomName = cleanRoomName(roomName);
		String salt = "";
		String hash = "";
		if (password != null && !password.isBlank()) {
			byte[] saltBytes = new byte[16];
			secureRandom.nextBytes(saltBytes);
			salt = Base64.getEncoder().encodeToString(saltBytes);
			hash = hashPassword(password, saltBytes);
		}

		String normalizedName = normalizeRoomName(cleanRoomName);
		if (rooms.putIfAbsent(normalizedName,
				new GameRoom(cleanRoomName, salt, hash, defaultRoomConfig)) == null) {
			return cleanRoomName;
		}
		throw new RoomException(HttpStatus.CONFLICT, "ROOM_NAME_TAKEN", "이미 사용 중인 방 이름입니다.");
	}

	public RoomDtos.JoinRoomResponse joinRoom(String roomName, String nickname, String password) {
		GameRoom room = getRoom(roomName);
		String cleanNickname = cleanNickname(nickname);

		synchronized (room) {
			verifyPassword(room, password);
			if (!"WAITING".equals(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "ROOM_ALREADY_STARTED", "이미 시작된 방에는 입장할 수 없습니다.");
			}
			if (room.players.size() >= GameRoom.MAX_PLAYERS) {
				throw new RoomException(HttpStatus.CONFLICT, "ROOM_FULL", "방 인원이 가득 찼습니다.");
			}
			requireUniqueNickname(room, cleanNickname, null);

			String playerId = UUID.randomUUID().toString();
			String token = generateSessionToken();
			GameRoom.Player player = new GameRoom.Player(playerId, cleanNickname);
			room.players.put(playerId, player);
			if (room.hostPlayerId == null) {
				room.hostPlayerId = playerId;
			}
			sessions.put(token, new PlayerSession(token, room.name, playerId));
			playerSessions.put(playerId, new PlayerSession(token, room.name, playerId));
			broadcastRoom(room);
			return new RoomDtos.JoinRoomResponse(room.name, player.nickname, token);
		}
	}

	public RoomView getPublicRoom(String roomName) {
		GameRoom room = getRoom(roomName);
		synchronized (room) {
			return toRoomView(room);
		}
	}

	public List<RoomSummary> getWaitingRooms() {
		return rooms.values().stream()
				.map(room -> {
					synchronized (room) {
						if (!"WAITING".equals(room.phase) || room.players.isEmpty()) {
							return null;
						}
						return new RoomSummary(room.name, room.players.size(), GameRoom.MAX_PLAYERS,
								!room.passwordHash.isEmpty());
					}
				})
				.filter(java.util.Objects::nonNull)
				.sorted(java.util.Comparator.comparing(RoomSummary::roomName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public PlayerSession authenticate(String token) {
		PlayerSession session = sessions.get(token);
		if (session == null) {
			throw new RoomException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		GameRoom room = getRoom(session.roomName());
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

	public GameRoom.Player requirePlayer(PlayerSession session, String roomName) {
		if (!normalizeRoomName(session.roomName()).equals(normalizeRoomName(roomName))) {
			throw new RoomException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "이 방에 접근할 권한이 없습니다.");
		}
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			GameRoom.Player player = room.players.get(session.playerId());
			if (player == null) {
				throw new RoomException(HttpStatus.FORBIDDEN, "ROOM_ACCESS_DENIED", "이 방에 접근할 권한이 없습니다.");
			}
			return player;
		}
	}

	Role roleOf(PlayerSession session) {
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			return requirePlayer(session, room.name).role;
		}
	}

	public void sendSnapshot(PlayerSession session, String roomName) {
		GameRoom room = getRoom(roomName);
		synchronized (room) {
			GameRoom.Player player = requirePlayer(session, room.name);
			messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
					RoomDtos.RoomEvent.of("snapshot",
							new RoomDtos.PrivateSnapshot(toRoomView(room), player.nickname, player.role,
									"WAITING".equals(room.phase) ? null : walletOf(player))));
		}
	}

	public void setReady(PlayerSession session, String roomName, boolean ready) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireWaiting(room);
			room.players.get(session.playerId()).ready = ready;
			broadcastRoom(room);
		}
	}

	public void updateSettings(PlayerSession session, String roomName, RoomConfig config) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireHost(room, session);
			requireWaiting(room);
			room.config = config;
			broadcastRoom(room);
		}
	}

	public void renameRoom(PlayerSession session, String roomName, String newRoomName) {
		String cleanRoomName = cleanRoomName(newRoomName);
		String normalizedNewName = normalizeRoomName(cleanRoomName);
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireHost(room, session);
			requireWaiting(room);
			if (room.name.equals(cleanRoomName)) {
				return;
			}
			String normalizedOldName = normalizeRoomName(room.name);
			if (!normalizedOldName.equals(normalizedNewName)) {
				if (rooms.putIfAbsent(normalizedNewName, room) != null) {
					throw new RoomException(HttpStatus.CONFLICT, "ROOM_NAME_TAKEN", "이미 사용 중인 방 이름입니다.");
				}
				rooms.remove(normalizedOldName, room);
			}
			room.name = cleanRoomName;
			for (String playerId : room.players.keySet()) {
				PlayerSession current = playerSessions.get(playerId);
				if (current == null) {
					continue;
				}
				PlayerSession renamed = new PlayerSession(current.token(), cleanRoomName, playerId);
				playerSessions.put(playerId, renamed);
				sessions.put(current.token(), renamed);
			}
			// 방 토픽 주소가 바뀌므로 개인 큐로 알려 각자 새 토픽을 다시 구독하게 한다.
			for (String playerId : room.players.keySet()) {
				messagingTemplate.getObject().convertAndSendToUser(playerId, "/queue/private",
						RoomDtos.RoomEvent.of("roomRenamed", new RoomDtos.RoomRenamed(cleanRoomName)));
			}
			broadcastRoom(room);
		}
	}

	public void changeNickname(PlayerSession session, String roomName, String newNickname) {
		String cleanNickname = cleanNickname(newNickname);
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireWaiting(room);
			GameRoom.Player player = requirePlayer(session, room.name);
			if (player.nickname.equals(cleanNickname)) {
				return;
			}
			requireUniqueNickname(room, cleanNickname, player.id);
			player.nickname = cleanNickname;
			messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
					RoomDtos.RoomEvent.of("nicknameChanged", new RoomDtos.NicknameChanged(cleanNickname)));
			broadcastRoom(room);
		}
	}

	public void startGame(PlayerSession session, String roomName) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireHost(room, session);
			requireWaiting(room);
			if (room.players.size() < minPlayers) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS",
						"게임 시작에는 " + minPlayers + "명 이상이 필요합니다.");
			}
			room.backgroundIndex = secureRandom.nextInt(backgroundCount);
			room.round = 0;
			for (GameRoom.Player player : room.players.values()) {
				player.gold = startGold;
				player.coins = 0;
				sendWallet(player);
			}
			startRound(room);
		}
	}

	public void exchangeCoin(PlayerSession session, String roomName) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			if ("WAITING".equals(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "GAME_NOT_STARTED", "게임 중에만 코인을 교환할 수 있습니다.");
			}
			GameRoom.Player player = requirePlayer(session, room.name);
			int price = room.config.coinPrice();
			if (player.gold < price) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_ENOUGH_GOLD",
						"골드가 부족합니다. (" + price + "G 필요)");
			}
			player.gold -= price;
			player.coins++;
			sendWallet(player);
		}
	}

	WalletView walletOf(PlayerSession session) {
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			return walletOf(requirePlayer(session, room.name));
		}
	}

	WalletView walletOf(GameRoom.Player player) {
		return new WalletView(player.gold, player.coins);
	}

	private void sendWallet(GameRoom.Player player) {
		messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
				RoomDtos.RoomEvent.of("wallet", walletOf(player)));
	}

	// 아래 게임 진행 메서드는 모두 room 잠금을 잡은 상태에서 호출한다.

	private void startRound(GameRoom room) {
		room.round++;
		room.turn = 0;
		assignRoles(room);
		for (GameRoom.Player player : room.players.values()) {
			messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
					RoomDtos.RoomEvent.of("role", new RoomDtos.RoleView(player.role)));
		}
		enterPhase(room, "PREPARE", roleRevealSeconds, () -> announceRoundStart(room));
	}

	private void announceRoundStart(GameRoom room) {
		enterPhase(room, "ROUND_START", roundStartSeconds, () -> startTurn(room));
	}

	private void startTurn(GameRoom room) {
		room.turn++;
		// TODO: 토론 종료 후 투표 → 턴 정산 단계로 이어진다.
		enterPhase(room, "DISCUSSION", room.config.turnDurationSeconds(), null);
	}

	private void assignRoles(GameRoom room) {
		List<GameRoom.Player> players = new ArrayList<>(room.players.values());
		Collections.shuffle(players, secureRandom);
		for (GameRoom.Player player : players) {
			player.role = Role.CITIZEN;
		}
		players.get(0).role = Role.THIEF;
		if (players.size() > 1 && secureRandom.nextDouble() < murdererChance) {
			players.get(1).role = Role.MURDERER;
		}
	}

	private void enterPhase(GameRoom room, String phase, long seconds, Runnable next) {
		room.phase = phase;
		room.phaseEndsAt = Instant.now().plusSeconds(seconds);
		long version = ++room.phaseVersion;
		cancelPhaseTask(room);
		if (next != null) {
			room.phaseTask = phaseScheduler.schedule(() -> {
				synchronized (room) {
					if (room.phaseVersion != version || room.players.isEmpty()) {
						return;
					}
					next.run();
				}
			}, seconds, TimeUnit.SECONDS);
		}
		broadcastRoom(room);
	}

	private void cancelPhaseTask(GameRoom room) {
		if (room.phaseTask != null) {
			room.phaseTask.cancel(false);
			room.phaseTask = null;
		}
	}

	public void sendChat(PlayerSession session, String roomName, String message) {
		GameRoom room = getMemberRoom(session, roomName);
		String cleanMessage = message == null ? "" : message.strip();
		if (cleanMessage.isBlank() || cleanMessage.codePointCount(0, cleanMessage.length()) > 500) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_MESSAGE", "메시지는 1~500자여야 합니다.");
		}
		synchronized (room) {
			GameRoom.Player player = requirePlayer(session, room.name);
			ChatMessage chatMessage = new ChatMessage(player.nickname, cleanMessage, Instant.now());
			messagingTemplate.getObject().convertAndSend(topic(room.name),
					RoomDtos.RoomEvent.of("chat", chatMessage));
		}
	}

	public void assertRoomSubscription(PlayerSession session, String roomName) {
		requirePlayer(session, roomName);
	}

	public void connected(PlayerSession session, String websocketSessionId) {
		if (websocketSessionId == null || websocketSessionId.isBlank()) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_WEBSOCKET_SESSION",
					"웹소켓 세션을 확인할 수 없습니다.");
		}
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			requirePlayer(session, room.name);
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
		GameRoom room = rooms.get(normalizeRoomName(session.roomName()));
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

	public void leave(PlayerSession session, String roomName) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			cancelPendingDisconnect(session.playerId());
			removePlayer(room, session.playerId());
		}
	}

	private GameRoom getMemberRoom(PlayerSession session, String roomName) {
		requirePlayer(session, roomName);
		return getRoom(session.roomName());
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
			if (activeConnections.containsKey(playerId)) {
				return;
			}
			removePlayer(room, playerId);
		}
	}

	private void removePlayer(GameRoom room, String playerId) {
		synchronized (room) {
			if (room.players.remove(playerId) == null) {
				return;
			}
			sessions.values().removeIf(session -> session.playerId().equals(playerId));
			playerSessions.remove(playerId);
			activeConnections.remove(playerId);
			if (playerId.equals(room.hostPlayerId)) {
				room.hostPlayerId = room.players.keySet().stream().findFirst().orElse(null);
			}
			if (room.players.isEmpty()) {
				cancelPhaseTask(room);
				rooms.remove(normalizeRoomName(room.name), room);
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
		phaseScheduler.shutdownNow();
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

	private String cleanRoomName(String roomName) {
		String cleanRoomName = roomName == null ? "" : roomName.strip();
		if (cleanRoomName.isBlank() || cleanRoomName.codePointCount(0, cleanRoomName.length()) > 24
				|| !cleanRoomName.matches("[\\p{IsHangul}A-Za-z0-9 _-]+")) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_ROOM_NAME",
					"방 이름은 1~24자의 한글, 영문, 숫자, 공백, 하이픈, 밑줄만 사용할 수 있습니다.");
		}
		return cleanRoomName;
	}

	private String cleanNickname(String nickname) {
		String cleanNickname = nickname == null ? "" : nickname.strip();
		if (cleanNickname.isBlank() || cleanNickname.codePointCount(0, cleanNickname.length()) > 20) {
			throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 1~20자여야 합니다.");
		}
		return cleanNickname;
	}

	private void requireUniqueNickname(GameRoom room, String nickname, String exceptPlayerId) {
		String normalizedNickname = normalizeNickname(nickname);
		boolean duplicate = room.players.values().stream()
				.filter(player -> !player.id.equals(exceptPlayerId))
				.anyMatch(player -> normalizeNickname(player.nickname).equals(normalizedNickname));
		if (duplicate) {
			throw new RoomException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
		}
	}

	private GameRoom getRoom(String roomName) {
		String normalizedName = normalizeRoomName(roomName);
		GameRoom room = rooms.get(normalizedName);
		if (room == null) {
			throw new RoomException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
		}
		return room;
	}

	private String normalizeRoomName(String roomName) {
		if (roomName == null || roomName.strip().isEmpty()
				|| roomName.strip().codePointCount(0, roomName.strip().length()) > 24
				|| !roomName.strip().matches("[\\p{IsHangul}A-Za-z0-9 _-]+")) {
			throw new RoomException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
		}
		return Normalizer.normalize(roomName.strip().replaceAll(" +", " "), Normalizer.Form.NFKC)
				.toLowerCase(Locale.ROOT);
	}

	private void requireWaiting(GameRoom room) {
		if (!"WAITING".equals(room.phase)) {
			throw new RoomException(HttpStatus.CONFLICT, "ROOM_NOT_WAITING", "대기 중인 방에서만 사용할 수 있습니다.");
		}
	}

	private void requireHost(GameRoom room, PlayerSession session) {
		requirePlayer(session, room.name);
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
		return new RoomView(room.name, room.phase, List.copyOf(players), room.config, room.backgroundIndex,
				room.round, room.turn, room.phaseEndsAt, minPlayers, maxTurns);
	}

	private void broadcastRoom(GameRoom room) {
		messagingTemplate.getObject().convertAndSend(topic(room.name),
				RoomDtos.RoomEvent.of("room", toRoomView(room)));
	}

	private String topic(String roomName) {
		return "/topic/room/" + roomName;
	}

	private String generateSessionToken() {
		byte[] token = new byte[32];
		secureRandom.nextBytes(token);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}

	private String normalizeNickname(String nickname) {
		return Normalizer.normalize(nickname.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	public record PlayerSession(String token, String roomName, String playerId) {
	}
}
