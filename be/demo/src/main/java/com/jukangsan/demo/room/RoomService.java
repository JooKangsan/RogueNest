package com.jukangsan.demo.room;

import com.jukangsan.demo.meta.CardCatalog;
import com.jukangsan.demo.room.RoomDtos.ChatMessage;
import com.jukangsan.demo.room.RoomDtos.PlayerView;
import com.jukangsan.demo.room.RoomDtos.RoomSummary;
import com.jukangsan.demo.room.RoomDtos.RoomView;
import com.jukangsan.demo.room.RoomDtos.MyVoteView;
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
	private final long finalVoteSeconds;
	private final long voteResultSeconds;
	private final long nightSeconds;
	private final long roundEndSeconds;
	private final CardCatalog cardCatalog;
	private final double cardUpgradeChance;
	private final int robberyLargeRoomPlayers;
	private final int robberyTargetsLarge;
	private final int robberyTargetsSmall;
	private final int robberyAmount;
	private final int killCost;
	private final int turnGold;
	private final int roundEndGold;

	public RoomService(
			ObjectProvider<SimpMessagingTemplate> messagingTemplate,
			CardCatalog cardCatalog,
			@Value("${game.card.upgrade-chance:0.2}") double cardUpgradeChance,
			@Value("${game.room.default-round-count:7}") int defaultRoundCount,
			@Value("${game.room.default-coin-price:50}") int defaultCoinPrice,
			@Value("${game.room.default-turn-duration-seconds:120}") int defaultTurnDurationSeconds,
			@Value("${game.room.disconnect-grace-seconds:30}") long disconnectGraceSeconds,
			@Value("${game.room.background-count:20}") int backgroundCount,
			@Value("${game.room.min-players:4}") int minPlayers,
			@Value("${game.economy.start-gold:5}") int startGold,
			@Value("${game.round.max-turns:7}") int maxTurns,
			@Value("${game.round.role-reveal-seconds:3}") long roleRevealSeconds,
			@Value("${game.round.start-announce-seconds:1}") long roundStartSeconds,
			@Value("${game.role.murderer-chance:0.3}") double murdererChance,
			@Value("${game.vote.final-seconds:30}") long finalVoteSeconds,
			@Value("${game.vote.result-seconds:5}") long voteResultSeconds,
			@Value("${game.round.night-seconds:10}") long nightSeconds,
			@Value("${game.round.end-seconds:30}") long roundEndSeconds,
			@Value("${game.thief.large-room-players:6}") int robberyLargeRoomPlayers,
			@Value("${game.thief.targets-large:2}") int robberyTargetsLarge,
			@Value("${game.thief.targets-small:1}") int robberyTargetsSmall,
			@Value("${game.thief.amount:1}") int robberyAmount,
			@Value("${game.murderer.kill-cost:1}") int killCost,
			@Value("${game.economy.turn-gold:1}") int turnGold,
			@Value("${game.economy.round-end-gold:3}") int roundEndGold
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
		if (finalVoteSeconds < 1 || voteResultSeconds < 1 || nightSeconds < 1 || roundEndSeconds < 1) {
			throw new IllegalArgumentException("투표/밤/라운드 종료 시간은 1초 이상이어야 합니다.");
		}
		this.finalVoteSeconds = finalVoteSeconds;
		this.voteResultSeconds = voteResultSeconds;
		this.nightSeconds = nightSeconds;
		this.roundEndSeconds = roundEndSeconds;
		if (cardUpgradeChance < 0 || cardUpgradeChance > 1) {
			throw new IllegalArgumentException("카드 상위 등급 확률은 0~1 사이여야 합니다.");
		}
		this.cardCatalog = cardCatalog;
		this.cardUpgradeChance = cardUpgradeChance;
		if (robberyTargetsLarge < 0 || robberyTargetsSmall < 0 || robberyAmount < 0 || killCost < 0) {
			throw new IllegalArgumentException("강탈 인원/금액과 살해 비용은 0 이상이어야 합니다.");
		}
		this.robberyLargeRoomPlayers = robberyLargeRoomPlayers;
		this.robberyTargetsLarge = robberyTargetsLarge;
		this.robberyTargetsSmall = robberyTargetsSmall;
		this.robberyAmount = robberyAmount;
		this.killCost = killCost;
		if (turnGold < 0 || roundEndGold < 0) {
			throw new IllegalArgumentException("턴/라운드 종료 골드는 0 이상이어야 합니다.");
		}
		this.turnGold = turnGold;
		this.roundEndGold = roundEndGold;
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
									"WAITING".equals(room.phase) ? null : walletOf(player),
									myVoteOf(room, player), new RoomDtos.MyCardView(player.cardId),
									myKillTargetOf(room, player))));
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

	public void castVote(PlayerSession session, String roomName, String targetNickname, boolean abstain) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			if (!"DISCUSSION".equals(room.phase) && !"VOTE".equals(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_VOTING_TIME", "투표 시간이 아닙니다.");
			}
			GameRoom.Player voter = requirePlayer(session, room.name);
			if (!voter.alive) {
				throw new RoomException(HttpStatus.CONFLICT, "DEAD_PLAYER", "사망한 플레이어는 투표할 수 없습니다.");
			}
			if (abstain) {
				voter.vote = GameRoom.ABSTAIN;
			} else {
				GameRoom.Player target = room.players.values().stream()
						.filter(player -> player.nickname.equals(targetNickname))
						.findFirst()
						.orElseThrow(() -> new RoomException(HttpStatus.BAD_REQUEST, "INVALID_VOTE_TARGET",
								"투표 대상을 찾을 수 없습니다."));
				if (!target.alive) {
					throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_VOTE_TARGET",
							"사망한 플레이어에게는 투표할 수 없습니다.");
				}
				if (isTieTrial(room) && !room.trialPlayerIds.contains(target.id)) {
					throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_VOTE_TARGET",
							"중앙에 선 플레이어에게만 투표할 수 있습니다.");
				}
				voter.vote = target.id;
			}
			sendMyVote(room, voter);
			onVotesChanged(room);
		}
	}

	MyVoteView myVoteOf(PlayerSession session) {
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			return myVoteOf(room, requirePlayer(session, room.name));
		}
	}

	private MyVoteView myVoteOf(GameRoom room, GameRoom.Player player) {
		if (player.vote == null) {
			return new MyVoteView(null, false);
		}
		if (GameRoom.ABSTAIN.equals(player.vote)) {
			return new MyVoteView(null, true);
		}
		GameRoom.Player target = room.players.get(player.vote);
		return new MyVoteView(target == null ? null : target.nickname, false);
	}

	private void sendMyVote(GameRoom room, GameRoom.Player player) {
		messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
				RoomDtos.RoomEvent.of("myVote", myVoteOf(room, player)));
	}

	// ---- 살인마 밤 행동 ----
	// 밤 동안 살해 대상을 고르고(바꾸거나 취소 가능), 밤이 끝날 때 서버가 실행한다.

	public void chooseKillTarget(PlayerSession session, String roomName, String targetNickname) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			if (!"NIGHT".equals(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_NIGHT", "밤에만 살해 대상을 고를 수 있습니다.");
			}
			GameRoom.Player murderer = requirePlayer(session, room.name);
			if (murderer.role != Role.MURDERER) {
				throw new RoomException(HttpStatus.FORBIDDEN, "NOT_MURDERER", "살인마만 사용할 수 있습니다.");
			}
			if (!murderer.alive) {
				throw new RoomException(HttpStatus.CONFLICT, "DEAD_PLAYER", "사망한 플레이어는 행동할 수 없습니다.");
			}
			if (targetNickname == null) {
				murderer.killTarget = null;
			} else {
				if (murderer.gold < killCost) {
					throw new RoomException(HttpStatus.CONFLICT, "NOT_ENOUGH_GOLD",
							"골드가 부족합니다. (" + killCost + "G 필요)");
				}
				GameRoom.Player target = room.players.values().stream()
						.filter(player -> player.nickname.equals(targetNickname))
						.findFirst()
						.orElseThrow(() -> new RoomException(HttpStatus.BAD_REQUEST, "INVALID_KILL_TARGET",
								"대상을 찾을 수 없습니다."));
				if (target == murderer || !target.alive) {
					throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_KILL_TARGET",
							"자신과 사망자는 고를 수 없습니다.");
				}
				murderer.killTarget = target.id;
			}
			sendMyKillTarget(room, murderer);
		}
	}

	RoomDtos.MyKillTargetView myKillTargetOf(PlayerSession session) {
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			return myKillTargetOf(room, requirePlayer(session, room.name));
		}
	}

	private RoomDtos.MyKillTargetView myKillTargetOf(GameRoom room, GameRoom.Player player) {
		GameRoom.Player target = player.killTarget == null ? null : room.players.get(player.killTarget);
		return new RoomDtos.MyKillTargetView(target == null ? null : target.nickname);
	}

	private void sendMyKillTarget(GameRoom room, GameRoom.Player player) {
		messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
				RoomDtos.RoomEvent.of("myKillTarget", myKillTargetOf(room, player)));
	}

	private void sendNotice(GameRoom.Player player, String message) {
		messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
				RoomDtos.RoomEvent.of("notice", new RoomDtos.NoticeView(message)));
	}

	// ---- 인물 카드 공개와 효과 ----

	public void revealCard(PlayerSession session, String roomName) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			if (!CARD_SHOP_PHASES.contains(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_IN_ROUND", "라운드 중에만 카드를 공개할 수 있습니다.");
			}
			GameRoom.Player player = requirePlayer(session, room.name);
			if (!player.alive) {
				throw new RoomException(HttpStatus.CONFLICT, "DEAD_PLAYER", "사망한 플레이어는 카드를 공개할 수 없습니다.");
			}
			if (player.cardId == null) {
				throw new RoomException(HttpStatus.CONFLICT, "NO_CARD", "공개할 인물 카드가 없습니다.");
			}
			if (player.cardRevealed) {
				return;
			}
			player.cardRevealed = true;
			broadcastRoom(room);
		}
	}

	// 지금 효력이 있는 카드 효과. 사망자는 보유 카드 효과가 끊긴다.
	private List<CardCatalog.CardEffect> effectsOf(GameRoom.Player player, CardCatalog.EffectTrigger trigger) {
		if (!player.alive) {
			return List.of();
		}
		return cardCatalog.effectsOf(player.cardId).stream()
				.filter(effect -> effect.trigger() == trigger)
				.toList();
	}

	// 플레이어 검거 시 카드 효과(칸, 뱀파이어헌터, 스피드런, 고인물 등)
	private void applyCatchCardEffects(GameRoom room, Role caughtRole) {
		for (GameRoom.Player player : alivePlayers(room)) {
			int gained = 0;
			for (CardCatalog.CardEffect effect : effectsOf(player, CardCatalog.EffectTrigger.PLAYER_CAUGHT)) {
				boolean roleMatches = effect.caughtRoles().contains(caughtRole.name());
				boolean inTime = effect.maxTurn() == null || room.turn <= effect.maxTurn();
				if (roleMatches && inTime && effect.action() == CardCatalog.EffectAction.GAIN_GOLD) {
					gained += effect.amount();
				}
			}
			if (gained > 0) {
				player.gold += gained;
				sendNotice(player, "인물 카드 효과로 " + gained + "G를 얻었습니다.");
			}
		}
	}

	// 카드 등급: 요청 키 → cards.json의 tier 값, 그리고 한 단계 위 등급
	private static final Map<String, String> CARD_TIERS = Map.of(
			"COMMON", "일반", "RARE", "희귀", "LEGENDARY", "전설");
	private static final Map<String, String> NEXT_CARD_TIER = Map.of("일반", "희귀", "희귀", "전설");
	private static final java.util.Set<String> CARD_SHOP_PHASES = java.util.Set.of(
			"PREPARE", "ROUND_START", "DISCUSSION", "VOTE", "RESULT", "NIGHT");

	public void drawCard(PlayerSession session, String roomName, String tierKey) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			if (!CARD_SHOP_PHASES.contains(room.phase)) {
				throw new RoomException(HttpStatus.CONFLICT, "CARD_SHOP_CLOSED", "지금은 인물 카드를 뽑을 수 없습니다.");
			}
			GameRoom.Player player = requirePlayer(session, room.name);
			if (!player.alive) {
				throw new RoomException(HttpStatus.CONFLICT, "DEAD_PLAYER", "사망한 플레이어는 카드를 뽑을 수 없습니다.");
			}
			String tier = CARD_TIERS.get(tierKey);
			if (tier == null) {
				throw new RoomException(HttpStatus.BAD_REQUEST, "INVALID_CARD_TIER", "카드 등급이 올바르지 않습니다.");
			}
			int price = cardsOfTier(tier).getFirst().price();
			if (player.gold < price) {
				throw new RoomException(HttpStatus.CONFLICT, "NOT_ENOUGH_GOLD",
						"골드가 부족합니다. (" + price + "G 필요)");
			}
			// 상위 등급 등장: 일반 → 희귀, 희귀 → 전설 (가격은 처음 고른 등급 그대로)
			String drawnTier = tier;
			if (NEXT_CARD_TIER.containsKey(tier) && secureRandom.nextDouble() < cardUpgradeChance) {
				drawnTier = NEXT_CARD_TIER.get(tier);
			}
			List<CardCatalog.CardDefinition> pool = cardsOfTier(drawnTier);
			player.gold -= price;
			// 한 번에 1장만 보유. 다시 뽑으면 교체된다.
			player.cardId = pool.get(secureRandom.nextInt(pool.size())).id();
			// 새로 뽑은 카드는 비공개. 공개했던 카드를 바꾸면 모두의 화면에서 내려간다.
			boolean wasRevealed = player.cardRevealed;
			player.cardRevealed = false;
			// TODO: 라운드별 카드 구매 내역 로깅
			sendWallet(player);
			sendMyCard(player);
			if (wasRevealed) {
				broadcastRoom(room);
			}
		}
	}

	String cardOf(PlayerSession session) {
		GameRoom room = getRoom(session.roomName());
		synchronized (room) {
			return requirePlayer(session, room.name).cardId;
		}
	}

	private List<CardCatalog.CardDefinition> cardsOfTier(String tier) {
		return cardCatalog.cards().stream().filter(card -> card.tier().equals(tier)).toList();
	}

	private void sendMyCard(GameRoom.Player player) {
		messagingTemplate.getObject().convertAndSendToUser(player.id, "/queue/private",
				RoomDtos.RoomEvent.of("myCard", new RoomDtos.MyCardView(player.cardId)));
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
		room.lastVoteResult = null;
		room.nightVictim = null;
		room.trialPlayerIds.clear();
		// 사망자는 다음 라운드에 부활하고, 인물 카드는 그 라운드에만 유효하다.
		for (GameRoom.Player player : room.players.values()) {
			player.alive = true;
			player.cardId = null;
			player.cardRevealed = false;
			player.killTarget = null;
			sendMyCard(player);
		}
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
		clearVotes(room);
		room.lastVoteResult = null;
		enterPhase(room, "DISCUSSION", room.config.turnDurationSeconds(), () -> endDiscussion(room));
	}

	// ---- 투표 진행 ----
	// 토론: 생존자 전원이 투표(기권 포함)하면 바로 끝난다.
	// 토론 종료: 기권(미투표 포함)이 절반 이상이면 아무도 안 잡히고, 아니면 최다 득표자가 광장 중앙에 선다.
	// 최종 투표(VOTE): 중앙이 1명이면 누구에게나 다시 투표할 수 있고, 최다 득표자가 바뀌면 중앙이 바뀌며 시간이 다시 시작된다.
	//   중앙이 2명 이상(동표)이면 그들에게만 투표/기권할 수 있다.
	// 최종 투표 종료: 기권이 절반 이상이면 아무도 안 잡히고, 아니면 중앙에서 최다 득표자 검거(동표면 전원 사망).

	private void onVotesChanged(GameRoom room) {
		if ("DISCUSSION".equals(room.phase)) {
			boolean everyoneVoted = alivePlayers(room).stream().allMatch(player -> player.vote != null);
			if (everyoneVoted) {
				endDiscussion(room);
				return;
			}
		} else if ("VOTE".equals(room.phase) && room.trialPlayerIds.size() == 1) {
			List<String> leaders = voteLeaders(room, null);
			if (!leaders.isEmpty() && !leaders.equals(room.trialPlayerIds)) {
				startFinalVote(room, leaders);
				return;
			}
		}
		broadcastRoom(room);
	}

	private void endDiscussion(GameRoom room) {
		List<String> leaders = voteLeaders(room, null);
		if (abstainIsHalfOrMore(room) || leaders.isEmpty()) {
			showVoteResult(room, List.of());
			return;
		}
		startFinalVote(room, leaders);
	}

	private void startFinalVote(GameRoom room, List<String> leaders) {
		room.trialPlayerIds.clear();
		room.trialPlayerIds.addAll(leaders);
		if (isTieTrial(room)) {
			// 동표면 중앙에 선 사람 외의 표는 무효로 돌린다.
			for (GameRoom.Player player : alivePlayers(room)) {
				if (player.vote != null && !GameRoom.ABSTAIN.equals(player.vote)
						&& !room.trialPlayerIds.contains(player.vote)) {
					player.vote = null;
					sendMyVote(room, player);
				}
			}
		}
		enterPhase(room, "VOTE", finalVoteSeconds, () -> endFinalVote(room));
	}

	private void endFinalVote(GameRoom room) {
		List<String> caught = abstainIsHalfOrMore(room)
				? List.of()
				: voteLeaders(room, room.trialPlayerIds);
		showVoteResult(room, caught);
	}

	private void showVoteResult(GameRoom room, List<String> caughtIds) {
		room.trialPlayerIds.clear();
		boolean thiefCaught = false;
		boolean murdererCaught = false;
		List<String> nicknames = new ArrayList<>();
		for (String id : caughtIds) {
			GameRoom.Player caught = room.players.get(id);
			if (caught == null) {
				continue;
			}
			caught.alive = false;
			caught.vote = null;
			nicknames.add(caught.nickname);
			thiefCaught |= caught.role == Role.THIEF;
			murdererCaught |= caught.role == Role.MURDERER;
		}
		applyCatchRewards(room, caughtIds, thiefCaught);
		for (String id : caughtIds) {
			GameRoom.Player caught = room.players.get(id);
			if (caught != null) {
				applyCatchCardEffects(room, caught.role);
			}
		}
		room.players.values().forEach(this::sendWallet);
		String kind = caughtIds.isEmpty() ? "NO_ONE"
				: thiefCaught ? "THIEF_CAUGHT"
				: murdererCaught ? "MURDERER_CAUGHT"
				: "CITIZEN_DIED";
		room.lastVoteResult = new RoomDtos.VoteResultView(kind, List.copyOf(nicknames));
		boolean roundOver = thiefCaught;
		enterPhase(room, "RESULT", voteResultSeconds,
				() -> {
					if (roundOver) {
						endRound(room, true);
					} else {
						settleTurn(room);
					}
				});
	}

	// CLAUDE.md 검거/사망 결과 표
	private void applyCatchRewards(GameRoom room, List<String> caughtIds, boolean thiefCaught) {
		if (thiefCaught) {
			// 도적 검거: 생존 시민(살인마 포함) +1G. 도적은 골드를 잃지 않는다.
			for (GameRoom.Player player : alivePlayers(room)) {
				if (player.role != Role.THIEF) {
					player.gold += 1;
				}
			}
		} else {
			for (String id : caughtIds) {
				GameRoom.Player caught = room.players.get(id);
				if (caught == null) {
					continue;
				}
				if (caught.role == Role.MURDERER) {
					// 살인마 검거: 살인마 제외 생존자 전원 +1G (도적 포함, 사망자 제외)
					for (GameRoom.Player player : room.players.values()) {
						if (player != caught && player.alive) {
							player.gold += 1;
						}
					}
				} else {
					// 시민 사망(투표): 사망한 시민 +3G
					caught.gold += 3;
				}
			}
		}
		room.players.values().forEach(this::sendWallet);
	}

	// ---- 턴 정산 → 밤 → 다음 턴 ----

	private void settleTurn(GameRoom room) {
		if (citizenSafetyTriggered(room)) {
			endRound(room, false);
			return;
		}
		// 턴 정산 순서: 생존자 전원 1G → 도적 강탈 → 살인마 밤 행동
		for (GameRoom.Player player : alivePlayers(room)) {
			player.gold += turnGold;
			for (CardCatalog.CardEffect effect : effectsOf(player, CardCatalog.EffectTrigger.TURN_END)) {
				if (effect.action() == CardCatalog.EffectAction.GAIN_GOLD) {
					player.gold += effect.amount();
				}
			}
			sendWallet(player);
		}
		robRandomPlayers(room);
		room.nightVictim = null;
		for (GameRoom.Player player : room.players.values()) {
			if (player.killTarget != null) {
				player.killTarget = null;
				sendMyKillTarget(room, player);
			}
		}
		enterPhase(room, "NIGHT", nightSeconds, () -> afterNight(room));
	}

	// 도적 강탈: 사망자와 도적 자신을 뺀 생존자 중 랜덤으로 골라 빼앗는다. 골드가 없으면 빼앗지 못한다.
	private void robRandomPlayers(GameRoom room) {
		GameRoom.Player thief = alivePlayers(room).stream()
				.filter(player -> player.role == Role.THIEF)
				.findFirst()
				.orElse(null);
		if (thief == null) {
			return;
		}
		List<GameRoom.Player> candidates = new ArrayList<>(alivePlayers(room));
		candidates.remove(thief);
		Collections.shuffle(candidates, secureRandom);
		int targetCount = room.players.size() >= robberyLargeRoomPlayers ? robberyTargetsLarge : robberyTargetsSmall;
		List<String> robbed = new ArrayList<>();
		for (GameRoom.Player victim : candidates.subList(0, Math.min(targetCount, candidates.size()))) {
			int amount = Math.min(robberyAmount, victim.gold);
			if (amount <= 0) {
				continue;
			}
			victim.gold -= amount;
			thief.gold += amount;
			robbed.add(victim.nickname);
			sendWallet(victim);
			sendNotice(victim, "도적에게 " + amount + "G를 강탈당했습니다.");
		}
		if (!robbed.isEmpty()) {
			sendWallet(thief);
			sendNotice(thief, String.join(", ", robbed) + "님에게서 골드를 강탈했습니다.");
		}
	}

	private void afterNight(GameRoom room) {
		GameRoom.Player victim = carryOutKill(room);
		if (victim != null && victim.role == Role.THIEF) {
			thiefKilled(room, victim);
			return;
		}
		if (victim != null) {
			// 살해 결과는 다음 턴 시작 시 공개한다. 직업은 공개하지 않는다.
			room.nightVictim = victim.nickname;
			if (citizenSafetyTriggered(room)) {
				endRound(room, false);
				return;
			}
		}
		if (room.turn >= maxTurns) {
			endRound(room, false);
		} else {
			startTurn(room);
		}
	}

	// 살아 있는 살인마가 고른 대상을 살해한다. 비용이 없거나 대상이 이미 없으면 아무 일도 없다.
	private GameRoom.Player carryOutKill(GameRoom room) {
		for (GameRoom.Player murderer : room.players.values()) {
			String targetId = murderer.killTarget;
			if (targetId == null) {
				continue;
			}
			murderer.killTarget = null;
			sendMyKillTarget(room, murderer);
			GameRoom.Player target = room.players.get(targetId);
			if (murderer.role != Role.MURDERER || !murderer.alive || murderer.gold < killCost
					|| target == null || !target.alive) {
				continue;
			}
			murderer.gold -= killCost;
			sendWallet(murderer);
			target.alive = false;
			target.vote = null;
			return target;
		}
		return null;
	}

	// 살인마가 도적을 처치: 생존자 전원(도적·사망자 제외) +1G, 특별 알림 후 라운드 종료(도적 코인 없음)
	private void thiefKilled(GameRoom room, GameRoom.Player thief) {
		for (GameRoom.Player player : alivePlayers(room)) {
			if (player != thief) {
				player.gold += 1;
				sendWallet(player);
			}
		}
		room.lastVoteResult = new RoomDtos.VoteResultView("THIEF_KILLED", List.of(thief.nickname));
		enterPhase(room, "RESULT", voteResultSeconds, () -> endRound(room, true));
	}

	// 안전장치: 도적을 제외한 생존 시민(살인마 포함)이 1명 이하면 라운드를 끝낸다(도적 미검거).
	private boolean citizenSafetyTriggered(GameRoom room) {
		long aliveCitizens = alivePlayers(room).stream()
				.filter(player -> player.role != Role.THIEF)
				.count();
		return aliveCitizens <= 1;
	}

	// ---- 라운드 종료 → 다음 라운드 / 게임 종료 ----

	private void endRound(GameRoom room, boolean thiefCaught) {
		clearVotes(room);
		room.trialPlayerIds.clear();
		for (GameRoom.Player player : room.players.values()) {
			// 전원(사망자 포함) 라운드 종료 골드. 탐관오리는 보상을 늘린다(사망자는 효과 끊김).
			int reward = roundEndGold;
			for (CardCatalog.CardEffect effect : effectsOf(player, CardCatalog.EffectTrigger.ROUND_END)) {
				if (effect.action() == CardCatalog.EffectAction.SET_ROUND_REWARD) {
					reward = Math.max(reward, effect.amount());
				}
			}
			player.gold += reward;
			if (!thiefCaught && player.role == Role.THIEF) {
				player.coins += 1;
			}
			sendWallet(player);
		}
		// 라운드 종료 결과: 직업·골드·코인 공개. 코인 많은 순, 같으면 골드 많은 순.
		// TODO: 라운드별 로그(도적 생존 여부, 검거 턴, 카드 구매/시너지 내역)
		boolean lastRound = room.round >= room.config.roundCount();
		List<RoomDtos.RoundResultRow> rows = room.players.values().stream()
				.map(player -> new RoomDtos.RoundResultRow(player.nickname, player.role, player.gold, player.coins))
				.sorted(java.util.Comparator.comparingInt(RoomDtos.RoundResultRow::coins)
						.thenComparingInt(RoomDtos.RoundResultRow::gold)
						.reversed())
				.toList();
		room.roundSummary = new RoomDtos.RoundSummaryView(room.round, thiefCaught, lastRound, rows);
		if (lastRound) {
			enterPhase(room, "GAME_OVER", roundEndSeconds, () -> returnToWaiting(room));
		} else {
			enterPhase(room, "ROUND_END", roundEndSeconds, () -> startRound(room));
		}
	}

	// 라운드 종료/게임 종료 화면의 "다음 라운드" 버튼. 방장이 누르면 기다리지 않고 바로 넘어간다.
	public void skipRoundEnd(PlayerSession session, String roomName) {
		GameRoom room = getMemberRoom(session, roomName);
		synchronized (room) {
			requireHost(room, session);
			if ((!"ROUND_END".equals(room.phase) && !"GAME_OVER".equals(room.phase)) || room.phaseNext == null) {
				throw new RoomException(HttpStatus.CONFLICT, "CANNOT_SKIP", "지금은 넘길 수 없습니다.");
			}
			Runnable next = room.phaseNext;
			cancelPhaseTask(room);
			room.phaseNext = null;
			next.run();
		}
	}

	private void returnToWaiting(GameRoom room) {
		room.roundSummary = null;
		room.phaseNext = null;
		room.round = 0;
		room.turn = 0;
		room.backgroundIndex = null;
		room.lastVoteResult = null;
		room.nightVictim = null;
		room.trialPlayerIds.clear();
		for (GameRoom.Player player : room.players.values()) {
			player.killTarget = null;
			player.ready = false;
			player.role = null;
			player.alive = true;
			player.vote = null;
			player.cardId = null;
			player.cardRevealed = false;
			sendMyCard(player);
		}
		cancelPhaseTask(room);
		room.phase = "WAITING";
		room.phaseEndsAt = null;
		room.phaseVersion++;
		broadcastRoom(room);
	}

	// ---- 투표 계산 도우미 ----

	private List<GameRoom.Player> alivePlayers(GameRoom room) {
		return room.players.values().stream().filter(player -> player.alive).toList();
	}

	private boolean isTieTrial(GameRoom room) {
		return "VOTE".equals(room.phase) && room.trialPlayerIds.size() > 1;
	}

	// 기권 + 미투표가 생존자의 절반 이상인지
	private boolean abstainIsHalfOrMore(GameRoom room) {
		List<GameRoom.Player> alive = alivePlayers(room);
		long abstained = alive.stream()
				.filter(player -> player.vote == null || GameRoom.ABSTAIN.equals(player.vote))
				.count();
		return abstained * 2 >= alive.size();
	}

	// 가장 많이 득표한 생존자 id 목록(1표 이상). candidates가 있으면 그 안에서만 센다.
	private List<String> voteLeaders(GameRoom room, List<String> candidates) {
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (GameRoom.Player target : alivePlayers(room)) {
			if (candidates == null || candidates.contains(target.id)) {
				counts.put(target.id, 0);
			}
		}
		for (GameRoom.Player voter : alivePlayers(room)) {
			if (voter.vote != null && counts.containsKey(voter.vote)) {
				counts.merge(voter.vote, 1, Integer::sum);
			}
		}
		int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		if (max == 0) {
			return List.of();
		}
		return counts.entrySet().stream()
				.filter(entry -> entry.getValue() == max)
				.map(java.util.Map.Entry::getKey)
				.toList();
	}

	private void clearVotes(GameRoom room) {
		for (GameRoom.Player player : room.players.values()) {
			player.vote = null;
			sendMyVote(room, player);
		}
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
		room.phaseNext = next;
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
			GameRoom.Player removed = room.players.remove(playerId);
			if (removed == null) {
				return;
			}
			room.trialPlayerIds.remove(playerId);
			sessions.values().removeIf(session -> session.playerId().equals(playerId));
			playerSessions.remove(playerId);
			activeConnections.remove(playerId);
			if (playerId.equals(room.hostPlayerId)) {
				room.hostPlayerId = room.players.keySet().stream().findFirst().orElse(null);
			}
			if (room.players.isEmpty()) {
				cancelPhaseTask(room);
				rooms.remove(normalizeRoomName(room.name), room);
			} else if (CARD_SHOP_PHASES.contains(room.phase) && removed.role == Role.THIEF && removed.alive) {
				// 라운드 중 살아 있는 도적이 나가면 도적 검거로 처리한다.
				thiefLeft(room, removed.nickname);
			} else if ("DISCUSSION".equals(room.phase) || "VOTE".equals(room.phase)) {
				// 나간 사람을 빼고 투표 상태를 다시 판단한다. (남은 전원이 투표했으면 토론 종료 등)
				onVotesChanged(room);
			} else {
				broadcastRoom(room);
			}
		}
	}

	private void thiefLeft(GameRoom room, String thiefNickname) {
		clearVotes(room);
		room.trialPlayerIds.clear();
		// 도적 검거 보상: 생존 시민(살인마 포함) +1G
		for (GameRoom.Player player : alivePlayers(room)) {
			player.gold += 1;
			sendWallet(player);
		}
		applyCatchCardEffects(room, Role.THIEF);
		room.players.values().forEach(this::sendWallet);
		room.lastVoteResult = new RoomDtos.VoteResultView("THIEF_LEFT", List.of(thiefNickname));
		enterPhase(room, "RESULT", voteResultSeconds, () -> endRound(room, true));
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
			long votes = room.players.values().stream()
					.filter(voter -> player.id.equals(voter.vote))
					.count();
			players.add(new PlayerView(player.nickname,
					player.id.equals(room.hostPlayerId), player.ready, player.vote != null, (int) votes,
					player.alive, room.trialPlayerIds.contains(player.id),
					player.cardRevealed ? player.cardId : null));
		}
		return new RoomView(room.name, room.phase, List.copyOf(players), room.config, room.backgroundIndex,
				room.round, room.turn, room.phaseEndsAt, minPlayers, maxTurns,
				"RESULT".equals(room.phase) ? room.lastVoteResult : null,
				roundSummaryView(room), room.nightVictim);
	}

	private RoomDtos.RoundSummaryView roundSummaryView(GameRoom room) {
		if (room.roundSummary == null
				|| (!"ROUND_END".equals(room.phase) && !"GAME_OVER".equals(room.phase))) {
			return null;
		}
		return room.roundSummary;
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
