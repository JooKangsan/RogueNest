package com.jukangsan.demo.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"game.round.role-reveal-seconds=1",
		"game.round.start-announce-seconds=1",
		"game.role.murderer-chance=1",
		"game.economy.start-gold=60",
		"game.vote.final-seconds=1",
		"game.vote.result-seconds=1",
		"game.round.night-seconds=2",
		"game.round.end-seconds=1",
		"game.card.upgrade-chance=0"
})
class GameFlowTests {
	@Autowired
	private RoomService roomService;

	@Autowired
	private com.jukangsan.demo.meta.CardCatalog cardCatalog;

	// 단계 전환은 서버 스케줄러가 하므로 정확한 시각을 가정하지 않는다.
	// 조건이 맞을 때까지 짧은 간격으로 확인하고, 제한 시간이 지나면 실패한다.
	private RoomDtos.RoomView awaitRoom(String roomName, java.util.function.Predicate<RoomDtos.RoomView> condition)
			throws InterruptedException {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
		RoomDtos.RoomView room = roomService.getPublicRoom(roomName);
		while (!condition.test(room)) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("기다리던 상태가 되지 않았습니다. 현재 단계: " + room.phase()
						+ ", 턴: " + room.turn());
			}
			Thread.sleep(20);
			room = roomService.getPublicRoom(roomName);
		}
		return room;
	}

	private RoomDtos.RoomView awaitPhase(String roomName, String phase) throws InterruptedException {
		return awaitRoom(roomName, room -> room.phase().equals(phase));
	}

	// 밤이 지나 다음 턴 토론이 시작될 때까지
	private RoomDtos.RoomView awaitDiscussion(String roomName, int turn) throws InterruptedException {
		return awaitRoom(roomName, room -> room.phase().equals("DISCUSSION") && room.turn() == turn);
	}

	private String tierOf(String cardId) {
		return cardCatalog.cards().stream().filter(card -> card.id().equals(cardId)).findFirst().orElseThrow().tier();
	}

	@Test
	void startingGameAssignsRolesAndAdvancesToFirstDiscussion() throws Exception {
		String roomName = roomService.createRoom("Flow Lobby", null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : new String[] {"방장", "둘", "셋", "넷", "다섯"}) {
			var joined = roomService.joinRoom(roomName, nickname, null);
			sessions.add(roomService.authenticate(joined.sessionToken()));
		}

		roomService.startGame(sessions.getFirst(), roomName);

		var prepare = roomService.getPublicRoom(roomName);
		assertThat(prepare.phase()).isEqualTo("PREPARE");
		assertThat(prepare.round()).isEqualTo(1);
		assertThat(prepare.phaseEndsAt()).isAfter(Instant.now());
		List<Role> roles = sessions.stream().map(roomService::roleOf).toList();
		assertThat(roles).containsOnlyOnce(Role.THIEF).containsOnlyOnce(Role.MURDERER);
		assertThat(roles).filteredOn(role -> role == Role.CITIZEN).hasSize(3);

		awaitPhase(roomName, "ROUND_START");

		var discussion = awaitPhase(roomName, "DISCUSSION");
		assertThat(discussion.turn()).isEqualTo(1);
		assertThat(discussion.phaseEndsAt()).isAfter(Instant.now().plusSeconds(100));
	}

	@Test
	void exchangingCoinSpendsGoldAndRejectsWhenNotEnough() {
		String roomName = roomService.createRoom("Coin Lobby", null);
		var joined = roomService.joinRoom(roomName, "혼자", null);
		RoomService.PlayerSession session = roomService.authenticate(joined.sessionToken());

		assertThatThrownBy(() -> roomService.exchangeCoin(session, roomName))
				.isInstanceOf(RoomException.class);

		for (String nickname : new String[] {"둘", "셋", "넷"}) {
			roomService.joinRoom(roomName, nickname, null);
		}
		roomService.startGame(session, roomName);
		assertThat(roomService.walletOf(session)).isEqualTo(new RoomDtos.WalletView(60, 0));

		roomService.exchangeCoin(session, roomName);

		assertThat(roomService.walletOf(session)).isEqualTo(new RoomDtos.WalletView(10, 1));
		assertThatThrownBy(() -> roomService.exchangeCoin(session, roomName))
				.isInstanceOf(RoomException.class);
		assertThat(roomService.walletOf(session)).isEqualTo(new RoomDtos.WalletView(10, 1));
	}

	@Test
	void votingIsOnlyAllowedDuringDiscussionAndCanBeChanged() throws Exception {
		String roomName = roomService.createRoom("Vote Lobby", null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : new String[] {"하나", "둘", "셋", "넷"}) {
			var joined = roomService.joinRoom(roomName, nickname, null);
			sessions.add(roomService.authenticate(joined.sessionToken()));
		}
		RoomService.PlayerSession voter = sessions.getFirst();
		roomService.startGame(voter, roomName);

		assertThatThrownBy(() -> roomService.castVote(voter, roomName, "둘", false))
				.isInstanceOf(RoomException.class);

		awaitPhase(roomName, "DISCUSSION");

		roomService.castVote(voter, roomName, "둘", false);
		assertThat(roomService.myVoteOf(voter)).isEqualTo(new RoomDtos.MyVoteView("둘", false));
		var players = roomService.getPublicRoom(roomName).players();
		assertThat(players).filteredOn(RoomDtos.PlayerView::voted).extracting(RoomDtos.PlayerView::nickname)
				.containsExactly("하나");

		roomService.castVote(sessions.get(1), roomName, "둘", false);
		roomService.castVote(sessions.get(2), roomName, "둘", false);
		assertThat(roomService.getPublicRoom(roomName).players())
				.extracting(RoomDtos.PlayerView::nickname, RoomDtos.PlayerView::votes)
				.contains(org.assertj.core.groups.Tuple.tuple("둘", 3), org.assertj.core.groups.Tuple.tuple("하나", 0));

		roomService.castVote(voter, roomName, "하나", false);
		assertThat(roomService.myVoteOf(voter)).isEqualTo(new RoomDtos.MyVoteView("하나", false));
		assertThat(roomService.getPublicRoom(roomName).players())
				.extracting(RoomDtos.PlayerView::nickname, RoomDtos.PlayerView::votes)
				.contains(org.assertj.core.groups.Tuple.tuple("둘", 2), org.assertj.core.groups.Tuple.tuple("하나", 1));

		roomService.castVote(voter, roomName, null, true);
		assertThat(roomService.myVoteOf(voter)).isEqualTo(new RoomDtos.MyVoteView(null, true));

		assertThatThrownBy(() -> roomService.castVote(voter, roomName, "없는사람", false))
				.isInstanceOf(RoomException.class);
	}

	private List<RoomService.PlayerSession> startDiscussion(String roomName, String... nicknames) throws Exception {
		roomService.createRoom(roomName, null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : nicknames) {
			var joined = roomService.joinRoom(roomName, nickname, null);
			sessions.add(roomService.authenticate(joined.sessionToken()));
		}
		roomService.startGame(sessions.getFirst(), roomName);
		awaitPhase(roomName, "DISCUSSION");
		return sessions;
	}

	private RoomDtos.PlayerView viewOf(String roomName, String nickname) {
		return roomService.getPublicRoom(roomName).players().stream()
				.filter(player -> player.nickname().equals(nickname))
				.findFirst()
				.orElseThrow();
	}

	@Test
	void everyoneVotingEndsDiscussionAndTopVotedGoesToCenterThenGetsCaught() throws Exception {
		String roomName = "Trial Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");

		roomService.castVote(sessions.get(0), roomName, "나", false);
		roomService.castVote(sessions.get(2), roomName, "나", false);
		roomService.castVote(sessions.get(3), roomName, "다", false);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("DISCUSSION");
		roomService.castVote(sessions.get(1), roomName, null, true);

		var trial = roomService.getPublicRoom(roomName);
		assertThat(trial.phase()).isEqualTo("VOTE");
		assertThat(viewOf(roomName, "나").onTrial()).isTrue();
		assertThat(viewOf(roomName, "다").onTrial()).isFalse();
		Role caughtRole = roomService.roleOf(sessions.get(1));

		var result = awaitPhase(roomName, "RESULT");
		assertThat(result.voteResult().nicknames()).containsExactly("나");
		assertThat(result.voteResult().kind()).isEqualTo(switch (caughtRole) {
			case THIEF -> "THIEF_CAUGHT";
			case MURDERER -> "MURDERER_CAUGHT";
			case CITIZEN -> "CITIZEN_DIED";
		});
		assertThat(viewOf(roomName, "나").alive()).isFalse();

		awaitPhase(roomName, caughtRole == Role.THIEF ? "ROUND_END" : "NIGHT");
	}

	@Test
	void newLeaderDuringFinalVoteTakesTheCenterAndTieLimitsTargets() throws Exception {
		String roomName = "Swap Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라", "마");

		roomService.castVote(sessions.get(0), roomName, "나", false);
		roomService.castVote(sessions.get(1), roomName, "나", false);
		roomService.castVote(sessions.get(2), roomName, "다", false);
		roomService.castVote(sessions.get(3), roomName, null, true);
		roomService.castVote(sessions.get(4), roomName, null, true);
		assertThat(viewOf(roomName, "나").onTrial()).isTrue();

		// 다가 3표로 역전 → 중앙이 다로 바뀐다
		roomService.castVote(sessions.get(1), roomName, "다", false);
		roomService.castVote(sessions.get(3), roomName, "다", false);
		assertThat(viewOf(roomName, "다").onTrial()).isTrue();
		assertThat(viewOf(roomName, "나").onTrial()).isFalse();

		// 마가 나로, 라가 기권으로 옮겨 나 2표 대 다 2표 동표 → 둘 다 중앙, 다른 사람에게는 투표 불가
		roomService.castVote(sessions.get(4), roomName, "나", false);
		roomService.castVote(sessions.get(3), roomName, null, true);
		assertThat(viewOf(roomName, "나").onTrial()).isTrue();
		assertThat(viewOf(roomName, "다").onTrial()).isTrue();
		assertThatThrownBy(() -> roomService.castVote(sessions.get(0), roomName, "라", false))
				.isInstanceOf(RoomException.class);
	}

	@Test
	void abstainHalfOrMoreCatchesNoOne() throws Exception {
		String roomName = "Abstain Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");

		roomService.castVote(sessions.get(0), roomName, "나", false);
		roomService.castVote(sessions.get(1), roomName, "다", false);
		roomService.castVote(sessions.get(2), roomName, null, true);
		roomService.castVote(sessions.get(3), roomName, null, true);

		var result = roomService.getPublicRoom(roomName);
		assertThat(result.phase()).isEqualTo("RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("NO_ONE");
		assertThat(result.players()).allMatch(RoomDtos.PlayerView::alive);
	}

	@Test
	void drawingCardSpendsGoldAndReplacesHeldCard() {
		String roomName = "Card Lobby";
		roomService.createRoom(roomName, null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : new String[] {"가", "나", "다", "라"}) {
			var joined = roomService.joinRoom(roomName, nickname, null);
			sessions.add(roomService.authenticate(joined.sessionToken()));
		}
		RoomService.PlayerSession me = sessions.getFirst();
		assertThatThrownBy(() -> roomService.drawCard(me, roomName, "COMMON"))
				.isInstanceOf(RoomException.class);

		roomService.startGame(me, roomName);
		roomService.drawCard(me, roomName, "COMMON");
		assertThat(roomService.walletOf(me).gold()).isEqualTo(59);
		assertThat(tierOf(roomService.cardOf(me))).isEqualTo("일반");

		roomService.drawCard(me, roomName, "LEGENDARY");
		assertThat(roomService.walletOf(me).gold()).isEqualTo(54);
		assertThat(tierOf(roomService.cardOf(me))).isEqualTo("전설");

		assertThatThrownBy(() -> roomService.drawCard(me, roomName, "MYTHIC"))
				.isInstanceOf(RoomException.class);
	}

	@Test
	void catchingThiefShowsRoundSummaryAndHostSkipsToNextRound() throws Exception {
		String roomName = "Round End Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		String[] nicknames = {"가", "나", "다", "라"};
		int thiefIndex = 0;
		for (int index = 0; index < sessions.size(); index++) {
			if (roomService.roleOf(sessions.get(index)) == Role.THIEF) {
				thiefIndex = index;
			}
		}
		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, nicknames[thiefIndex], false);
		}
		// 최종 투표 종료 → 결과 → 라운드 종료
		var roundEnd = awaitPhase(roomName, "ROUND_END");
		var summary = roundEnd.roundSummary();
		assertThat(summary.thiefCaught()).isTrue();
		assertThat(summary.finalRound()).isFalse();
		assertThat(summary.rows()).hasSize(4);
		assertThat(summary.rows()).extracting(RoomDtos.RoundResultRow::role).containsOnlyOnce(Role.THIEF);

		// 방장이 아니면 넘길 수 없고, 방장(첫 입장자 "가")이 누르면 바로 다음 라운드
		assertThatThrownBy(() -> roomService.skipRoundEnd(sessions.get(1), roomName))
				.isInstanceOf(RoomException.class);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("ROUND_END");
		roomService.skipRoundEnd(sessions.get(0), roomName);
		var next = roomService.getPublicRoom(roomName);
		assertThat(next.phase()).isEqualTo("PREPARE");
		assertThat(next.round()).isEqualTo(2);
		assertThat(next.roundSummary()).isNull();
	}

	@Test
	void thiefLeavingMidRoundCountsAsCaught() throws Exception {
		String roomName = "Leave Thief Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라", "마");
		RoomService.PlayerSession thief = sessions.stream()
				.filter(session -> roomService.roleOf(session) == Role.THIEF)
				.findFirst()
				.orElseThrow();
		RoomService.PlayerSession citizen = sessions.stream()
				.filter(session -> roomService.roleOf(session) != Role.THIEF)
				.findFirst()
				.orElseThrow();
		int goldBefore = roomService.walletOf(citizen).gold();

		roomService.leave(thief, roomName);

		var result = roomService.getPublicRoom(roomName);
		assertThat(result.phase()).isEqualTo("RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("THIEF_LEFT");
		assertThat(result.players()).hasSize(4);
		assertThat(roomService.walletOf(citizen).gold()).isEqualTo(goldBefore + 1);

		var roundEnd = awaitPhase(roomName, "ROUND_END");
		assertThat(roundEnd.roundSummary().thiefCaught()).isTrue();
	}

	private RoomService.PlayerSession sessionWithRole(List<RoomService.PlayerSession> sessions, Role role) {
		return sessions.stream().filter(session -> roomService.roleOf(session) == role).findFirst().orElseThrow();
	}

	private String nicknameOf(RoomService.PlayerSession session) {
		return roomService.requirePlayer(session, session.roomName()).nickname;
	}

	// 전원 기권 → 아무도 안 잡힘 → 결과(1초) → 턴 정산 후 밤
	private void abstainAllUntilNight(String roomName, List<RoomService.PlayerSession> sessions) throws Exception {
		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, null, true);
		}
		awaitPhase(roomName, "NIGHT");
	}

	@Test
	void thiefRobsOneRandomPlayerAtTurnSettlementInSmallRoom() throws Exception {
		String roomName = "Robbery Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);

		abstainAllUntilNight(roomName, sessions);

		// 생존자 전원 1G(60 → 61) 후 5명 이하이므로 1명에게서 1G 강탈
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(62);
		List<Integer> others = sessions.stream()
				.filter(session -> session != thief)
				.map(session -> roomService.walletOf(session).gold())
				.toList();
		assertThat(others).containsExactlyInAnyOrder(60, 61, 61);
	}

	@Test
	void murdererKillsChosenPlayerAtNightAndVictimIsAnnouncedNextTurn() throws Exception {
		String roomName = "Murder Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라", "마");
		RoomService.PlayerSession murderer = sessionWithRole(sessions, Role.MURDERER);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);
		String victim = nicknameOf(citizen);

		// 밤이 아니면 고를 수 없다
		assertThatThrownBy(() -> roomService.chooseKillTarget(murderer, roomName, victim))
				.isInstanceOf(RoomException.class);

		abstainAllUntilNight(roomName, sessions);
		int goldBefore = roomService.walletOf(murderer).gold();

		// 살인마가 아니거나, 자기 자신은 고를 수 없다
		assertThatThrownBy(() -> roomService.chooseKillTarget(citizen, roomName, nicknameOf(murderer)))
				.isInstanceOf(RoomException.class);
		assertThatThrownBy(() -> roomService.chooseKillTarget(murderer, roomName, nicknameOf(murderer)))
				.isInstanceOf(RoomException.class);

		roomService.chooseKillTarget(murderer, roomName, victim);
		assertThat(roomService.myKillTargetOf(murderer).target()).isEqualTo(victim);

		var nextTurn = awaitDiscussion(roomName, 2);
		assertThat(nextTurn.nightVictim()).isEqualTo(victim);
		assertThat(viewOf(roomName, victim).alive()).isFalse();
		assertThat(roomService.walletOf(murderer).gold()).isEqualTo(goldBefore - 1);
		assertThat(roomService.myKillTargetOf(murderer).target()).isNull();
	}

	@Test
	void murdererKillingThiefEndsRoundWithSpecialResult() throws Exception {
		String roomName = "Thief Kill Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession murderer = sessionWithRole(sessions, Role.MURDERER);
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);

		abstainAllUntilNight(roomName, sessions);
		int citizenGold = roomService.walletOf(citizen).gold();
		roomService.chooseKillTarget(murderer, roomName, nicknameOf(thief));

		var result = awaitPhase(roomName, "RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("THIEF_KILLED");
		assertThat(result.voteResult().nicknames()).containsExactly(nicknameOf(thief));
		assertThat(roomService.walletOf(citizen).gold()).isEqualTo(citizenGold + 1);

		var roundEnd = awaitPhase(roomName, "ROUND_END");
		assertThat(roundEnd.roundSummary().thiefCaught()).isTrue();
	}

	private void giveCard(RoomService.PlayerSession session, String cardId) {
		roomService.requirePlayer(session, session.roomName()).cardId = cardId;
	}

	@Test
	void revealingCardShowsItToEveryoneUntilReplaced() {
		String roomName = "Reveal Lobby";
		roomService.createRoom(roomName, null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : new String[] {"가", "나", "다", "라"}) {
			sessions.add(roomService.authenticate(roomService.joinRoom(roomName, nickname, null).sessionToken()));
		}
		RoomService.PlayerSession me = sessions.getFirst();
		roomService.startGame(me, roomName);

		assertThatThrownBy(() -> roomService.revealCard(me, roomName)).isInstanceOf(RoomException.class);

		roomService.drawCard(me, roomName, "COMMON");
		assertThat(viewOf(roomName, "가").revealedCardId()).isNull();
		roomService.revealCard(me, roomName);
		assertThat(viewOf(roomName, "가").revealedCardId()).isEqualTo(roomService.cardOf(me));

		// 새로 뽑으면 새 카드는 비공개
		roomService.drawCard(me, roomName, "COMMON");
		assertThat(viewOf(roomName, "가").revealedCardId()).isNull();
	}

	@Test
	void turnGoldCardAddsGoldAtTurnSettlement() throws Exception {
		String roomName = "Turn Card Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		giveCard(thief, "legendary_luck");

		abstainAllUntilNight(roomName, sessions);

		// 60 + 턴 1G + 행운(전설) 3G + 강탈 1G
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(65);
	}

	@Test
	void catchAndRoundEndCardsPayOutWhenThiefIsCaught() throws Exception {
		String roomName = "Catch Card Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		List<RoomService.PlayerSession> others = sessions.stream().filter(session -> session != thief).toList();
		RoomService.PlayerSession kan = others.get(0);
		RoomService.PlayerSession miser = others.get(1);
		RoomService.PlayerSession hunter = others.get(2);
		giveCard(kan, "legendary_kan");
		giveCard(miser, "legendary_miser");
		giveCard(hunter, "common_speedrun");

		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, nicknameOf(thief), false);
		}
		awaitPhase(roomName, "ROUND_END"); // 최종 투표 종료 → 결과 → 라운드 종료

		// 검거 +1G, 라운드 종료 3G(탐관오리는 10G), 칸 +8G, 스피드런(3턴 이내) +7G
		assertThat(roomService.walletOf(kan).gold()).isEqualTo(60 + 1 + 8 + 3);
		assertThat(roomService.walletOf(miser).gold()).isEqualTo(60 + 1 + 10);
		assertThat(roomService.walletOf(hunter).gold()).isEqualTo(60 + 1 + 7 + 3);
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(60 + 3);
	}

	private List<Integer> goldOfOthers(List<RoomService.PlayerSession> sessions, RoomService.PlayerSession except) {
		return sessions.stream()
				.filter(session -> session != except)
				.map(session -> roomService.walletOf(session).gold())
				.toList();
	}

	// 4명 방, 도적을 뺀 나머지 전원에게 같은 카드를 준다 (강탈 대상이 누구든 결과가 같도록)
	private List<RoomService.PlayerSession> othersHold(String roomName, String cardId) throws Exception {
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		sessions.stream().filter(session -> session != thief).forEach(session -> giveCard(session, cardId));
		return sessions;
	}

	@Test
	void steelHelmetNullifiesRobbery() throws Exception {
		String roomName = "Helmet Lobby";
		var sessions = othersHold(roomName, "common_steel_helmet");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);

		abstainAllUntilNight(roomName, sessions);

		assertThat(roomService.walletOf(thief).gold()).isEqualTo(61);
		assertThat(goldOfOthers(sessions, thief)).containsOnly(61);
	}

	@Test
	void vampireDrainsThiefWhenRobbed() throws Exception {
		String roomName = "Vampire Lobby";
		var sessions = othersHold(roomName, "legendary_vampire");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);

		abstainAllUntilNight(roomName, sessions);

		// 피해자: 61 - 1 + 3, 도적: 61 + 1 - 3
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(59);
		assertThat(goldOfOthers(sessions, thief)).containsExactlyInAnyOrder(63, 61, 61);
	}

	@Test
	void abyssSealsRobberyForNextTurn() throws Exception {
		String roomName = "Abyss Lobby";
		var sessions = othersHold(roomName, "common_abyss");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);

		abstainAllUntilNight(roomName, sessions);
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(62);
		awaitDiscussion(roomName, 2);
		abstainAllUntilNight(roomName, sessions);

		// 2턴 강탈은 봉인: 턴 1G만 받는다
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(63);
	}

	@Test
	void ninjaThiefRobsOneMorePlayer() throws Exception {
		String roomName = "Ninja Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		giveCard(thief, "common_self_proclaimed_ninja");

		abstainAllUntilNight(roomName, sessions);

		assertThat(roomService.walletOf(thief).gold()).isEqualTo(63);
		assertThat(goldOfOthers(sessions, thief)).containsExactlyInAnyOrder(60, 60, 61);
	}

	@Test
	void seaDragonBlocksFirstRobberyAndPaysAtRoundEndIfNeverRobbed() throws Exception {
		String roomName = "Sea Dragon Lobby";
		var sessions = othersHold(roomName, "common_sea_dragon");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);

		abstainAllUntilNight(roomName, sessions);
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(61);
		awaitDiscussion(roomName, 2);

		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, nicknameOf(thief), false);
		}
		awaitPhase(roomName, "ROUND_END"); // 최종 투표 → 결과 → 라운드 종료

		// 61 + 검거 1G + 라운드 종료 3G + 해룡 5G
		assertThat(goldOfOthers(sessions, thief)).containsOnly(70);
	}

	@Test
	void lawyerMurdererSurvivesCatchAsCitizen() throws Exception {
		String roomName = "Lawyer Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession murderer = sessionWithRole(sessions, Role.MURDERER);
		giveCard(murderer, "common_lawyer");

		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, nicknameOf(murderer), false);
		}
		var result = awaitPhase(roomName, "RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("MURDERER_CAUGHT");
		assertThat(viewOf(roomName, nicknameOf(murderer)).alive()).isTrue();
		assertThat(roomService.roleOf(murderer)).isEqualTo(Role.CITIZEN);
	}

	@Test
	void rareSteelHelmetNullifiesKill() throws Exception {
		String roomName = "Kill Helmet Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라", "마");
		RoomService.PlayerSession murderer = sessionWithRole(sessions, Role.MURDERER);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);
		giveCard(citizen, "rare_steel_helmet");

		abstainAllUntilNight(roomName, sessions);
		int goldBefore = roomService.walletOf(murderer).gold();
		roomService.chooseKillTarget(murderer, roomName, nicknameOf(citizen));
		var nextTurn = awaitDiscussion(roomName, 2);
		assertThat(nextTurn.nightVictim()).isNull();
		assertThat(viewOf(roomName, nicknameOf(citizen)).alive()).isTrue();
		assertThat(roomService.walletOf(murderer).gold()).isEqualTo(goldBefore - 1);
	}

	@Test
	void witchEndsRoundImmediatelyAndRevealsCard() throws Exception {
		String roomName = "Witch Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession witch = sessionWithRole(sessions, Role.CITIZEN);
		giveCard(witch, "legendary_witch");

		roomService.activateCard(witch, roomName, null);

		var result = roomService.getPublicRoom(roomName);
		assertThat(result.phase()).isEqualTo("RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("WITCH");
		assertThat(viewOf(roomName, nicknameOf(witch)).revealedCardId()).isEqualTo("legendary_witch");
		assertThatThrownBy(() -> roomService.activateCard(witch, roomName, null)).isInstanceOf(RoomException.class);

		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		var roundEnd = awaitPhase(roomName, "ROUND_END");
		assertThat(roundEnd.roundSummary().thiefCaught()).isFalse();
		// 전원 3G만 받고, 도적은 미검거지만 코인이 없다
		assertThat(roomService.walletOf(thief)).isEqualTo(new RoomDtos.WalletView(63, 0));
		assertThat(roomService.walletOf(witch).gold()).isEqualTo(63);
	}

	@Test
	void rainBlocksRobberyAndMurderer() throws Exception {
		String roomName = "Rain Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		RoomService.PlayerSession murderer = sessionWithRole(sessions, Role.MURDERER);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);
		giveCard(citizen, "rare_rainy_season");

		roomService.activateCard(citizen, roomName, null);
		assertThat(roomService.getPublicRoom(roomName).rainTurns()).isEqualTo(2);
		abstainAllUntilNight(roomName, sessions);

		assertThat(roomService.walletOf(thief).gold()).isEqualTo(61);
		assertThat(goldOfOthers(sessions, thief)).containsOnly(61);
		assertThatThrownBy(() -> roomService.chooseKillTarget(murderer, roomName, nicknameOf(citizen)))
				.isInstanceOf(RoomException.class);

		assertThat(awaitDiscussion(roomName, 2).rainTurns()).isEqualTo(1);
	}

	@Test
	void redCardCatchesThiefAndEndsRound() throws Exception {
		String roomName = "Red Card Thief Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);
		giveCard(citizen, "rare_red_card");

		roomService.activateCard(citizen, roomName, nicknameOf(thief));

		var result = roomService.getPublicRoom(roomName);
		assertThat(result.voteResult().kind()).isEqualTo("THIEF_CAUGHT");
		assertThat(viewOf(roomName, nicknameOf(citizen)).alive()).isTrue();
	}

	@Test
	void redCardOnCitizenTakesUserDownToo() throws Exception {
		String roomName = "Red Card Citizen Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라", "마");
		List<RoomService.PlayerSession> citizens = sessions.stream()
				.filter(session -> roomService.roleOf(session) == Role.CITIZEN)
				.toList();
		RoomService.PlayerSession user = citizens.get(0);
		RoomService.PlayerSession target = citizens.get(1);
		giveCard(user, "rare_red_card");
		// 레드카드는 대상이 필요하다
		assertThatThrownBy(() -> roomService.activateCard(user, roomName, null)).isInstanceOf(RoomException.class);

		roomService.activateCard(user, roomName, nicknameOf(target));

		assertThat(roomService.getPublicRoom(roomName).voteResult().kind()).isEqualTo("CITIZEN_DIED");
		assertThat(viewOf(roomName, nicknameOf(target)).alive()).isFalse();
		assertThat(viewOf(roomName, nicknameOf(user)).alive()).isFalse();
	}

	@Test
	void futurePersonPullsRemainingTurnGold() throws Exception {
		String roomName = "Future Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		giveCard(thief, "common_future_person");

		roomService.activateCard(thief, roomName, null);
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(60 + 7);

		abstainAllUntilNight(roomName, sessions);
		// 턴 골드는 더 받지 않고 강탈 1G만
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(68);
	}

	@Test
	void toughSisterStealsFromChosenPlayerAndWerewolfWaitsForTurnFour() throws Exception {
		String roomName = "Target Card Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession sister = sessions.get(0);
		RoomService.PlayerSession target = sessions.get(1);
		RoomService.PlayerSession wolf = sessions.get(2);
		giveCard(sister, "rare_tough_sister");
		giveCard(wolf, "legendary_werewolf");

		roomService.activateCard(sister, roomName, nicknameOf(target));
		assertThat(roomService.walletOf(sister).gold()).isEqualTo(62);
		assertThat(roomService.walletOf(target).gold()).isEqualTo(58);
		// 쎈 누나는 공개되지 않는 카드
		assertThat(viewOf(roomName, nicknameOf(sister)).revealedCardId()).isNull();

		assertThatThrownBy(() -> roomService.activateCard(wolf, roomName, nicknameOf(target)))
				.isInstanceOf(RoomException.class);
	}

	@Test
	void knowItAllRerollsOnceIntoHigherTierForFree() {
		String roomName = "Reroll Lobby";
		roomService.createRoom(roomName, null);
		List<RoomService.PlayerSession> sessions = new ArrayList<>();
		for (String nickname : new String[] {"가", "나", "다", "라"}) {
			sessions.add(roomService.authenticate(roomService.joinRoom(roomName, nickname, null).sessionToken()));
		}
		RoomService.PlayerSession me = sessions.getFirst();
		roomService.startGame(me, roomName);

		assertThatThrownBy(() -> roomService.freeReroll(me, roomName)).isInstanceOf(RoomException.class);

		giveCard(me, "common_know_it_all");
		roomService.freeReroll(me, roomName);
		assertThat(tierOf(roomService.cardOf(me))).isEqualTo("희귀");
		assertThat(roomService.walletOf(me).gold()).isEqualTo(60);

		giveCard(me, "rare_know_it_all");
		roomService.freeReroll(me, roomName);
		assertThat(tierOf(roomService.cardOf(me))).isEqualTo("전설");
		// 바뀐 카드에는 다시 뽑기 효과가 없다
		assertThatThrownBy(() -> roomService.freeReroll(me, roomName)).isInstanceOf(RoomException.class);
	}

	// 카드를 쥐여 주고 바로 공개한다.
	private void revealWith(RoomService.PlayerSession session, String cardId) {
		giveCard(session, cardId);
		roomService.revealCard(session, session.roomName());
	}

	private List<String> activeSynergyNames(String roomName) {
		return roomService.getPublicRoom(roomName).synergies().stream().map(RoomDtos.ActiveSynergyView::name).toList();
	}

	@Test
	void michuSynergyAddsTurnGoldOnceBothCardsAreRevealed() throws Exception {
		String roomName = "Michu Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		RoomService.PlayerSession citizen = sessionWithRole(sessions, Role.CITIZEN);

		revealWith(thief, "common_cutest");
		assertThat(activeSynergyNames(roomName)).isEmpty();
		revealWith(citizen, "common_ice_cream_smuggler");
		assertThat(activeSynergyNames(roomName)).containsExactly("미츄");

		abstainAllUntilNight(roomName, sessions);
		// 도적: 60 + 턴 1G + 미츄 1G + 강탈 1G
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(63);
	}

	@Test
	void dopamineUpgradeReplacesLowerReward() throws Exception {
		String roomName = "Dopamine Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");

		revealWith(sessions.get(0), "common_vampire");
		revealWith(sessions.get(1), "common_sea_dragon");
		assertThat(roomService.walletOf(sessions.get(0)).gold()).isEqualTo(63);

		// 3인이 되면 3G 효과가 5G로 교체: 기존 참여자는 차이 2G만 더 받는다
		revealWith(sessions.get(2), "common_abyss");
		assertThat(roomService.walletOf(sessions.get(0)).gold()).isEqualTo(65);
		assertThat(roomService.walletOf(sessions.get(1)).gold()).isEqualTo(65);
		// 심연은 후열의밤에도 속하지만 혼자라 발동하지 않는다
		assertThat(roomService.walletOf(sessions.get(2)).gold()).isEqualTo(65);
	}

	@Test
	void nightShiftPaysEveryParticipantWhenSomeoneJoins() throws Exception {
		String roomName = "Night Shift Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");

		revealWith(sessions.get(0), "rare_kan");
		assertThat(roomService.walletOf(sessions.get(0)).gold()).isEqualTo(60);
		revealWith(sessions.get(1), "rare_loafer");
		assertThat(roomService.walletOf(sessions.get(0)).gold()).isEqualTo(61);
		assertThat(roomService.walletOf(sessions.get(1)).gold()).isEqualTo(61);
		revealWith(sessions.get(2), "common_steel_helmet");
		assertThat(roomService.walletOf(sessions.get(0)).gold()).isEqualTo(62);
		assertThat(roomService.walletOf(sessions.get(1)).gold()).isEqualTo(62);
		assertThat(roomService.walletOf(sessions.get(2)).gold()).isEqualTo(61);
	}

	@Test
	void royalGivesEachParticipantACoin() throws Exception {
		String roomName = "Royal Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		String[] cards = {"legendary_werewolf", "legendary_loafer", "common_know_it_all", "legendary_kan"};
		for (int index = 0; index < 4; index++) {
			revealWith(sessions.get(index), cards[index]);
		}

		assertThat(activeSynergyNames(roomName)).contains("로열", "후열의밤");
		assertThat(sessions).allMatch(session -> roomService.walletOf(session).coins() == 1);
	}

	@Test
	void tongmoGivesGoldAndLegendaryRerollThatHidesTheNewCard() throws Exception {
		String roomName = "Tongmo Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession witch = sessions.get(0);
		revealWith(witch, "legendary_witch");
		revealWith(sessions.get(1), "legendary_werewolf");

		// 통모 +5G, 후열의밤(2인) +1G
		assertThat(roomService.walletOf(witch).gold()).isEqualTo(66);
		roomService.freeReroll(witch, roomName);
		assertThat(tierOf(roomService.cardOf(witch))).isEqualTo("전설");
		assertThat(viewOf(roomName, nicknameOf(witch)).revealedCardId()).isNull();
		assertThat(activeSynergyNames(roomName)).doesNotContain("통모");
		assertThatThrownBy(() -> roomService.freeReroll(witch, roomName)).isInstanceOf(RoomException.class);
	}

	@Test
	void pastPersonPaysAtRoundEndAndMakesNextRoundDrawFree() throws Exception {
		String roomName = "Past Person Lobby";
		var sessions = startDiscussion(roomName, "가", "나", "다", "라");
		RoomService.PlayerSession thief = sessionWithRole(sessions, Role.THIEF);
		RoomService.PlayerSession miser = sessionWithRole(sessions, Role.CITIZEN);
		RoomService.PlayerSession ninja = sessionWithRole(sessions, Role.MURDERER);
		revealWith(miser, "common_miser");
		revealWith(ninja, "common_self_proclaimed_ninja");

		for (RoomService.PlayerSession session : sessions) {
			roomService.castVote(session, roomName, nicknameOf(thief), false);
		}
		awaitPhase(roomName, "ROUND_END");
		// 60 + 검거 1G + 라운드 종료(탐관오리 5G) + 과거인 10G
		assertThat(roomService.walletOf(miser).gold()).isEqualTo(76);

		roomService.skipRoundEnd(sessions.getFirst(), roomName);
		int before = roomService.walletOf(miser).gold();
		roomService.drawCard(miser, roomName, "LEGENDARY");
		assertThat(roomService.walletOf(miser).gold()).isEqualTo(before);
		roomService.drawCard(miser, roomName, "COMMON");
		assertThat(roomService.walletOf(miser).gold()).isEqualTo(before - 1);
	}
}
