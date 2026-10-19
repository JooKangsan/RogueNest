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
		"game.round.night-seconds=1",
		"game.round.end-seconds=1",
		"game.card.upgrade-chance=0"
})
class GameFlowTests {
	@Autowired
	private RoomService roomService;

	@Autowired
	private com.jukangsan.demo.meta.CardCatalog cardCatalog;

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

		Thread.sleep(1_300);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("ROUND_START");

		Thread.sleep(1_000);
		var discussion = roomService.getPublicRoom(roomName);
		assertThat(discussion.phase()).isEqualTo("DISCUSSION");
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

		Thread.sleep(2_300);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("DISCUSSION");

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
		Thread.sleep(2_300);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("DISCUSSION");
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

		Thread.sleep(1_300);
		var result = roomService.getPublicRoom(roomName);
		assertThat(result.phase()).isEqualTo("RESULT");
		assertThat(result.voteResult().nicknames()).containsExactly("나");
		assertThat(result.voteResult().kind()).isEqualTo(switch (caughtRole) {
			case THIEF -> "THIEF_CAUGHT";
			case MURDERER -> "MURDERER_CAUGHT";
			case CITIZEN -> "CITIZEN_DIED";
		});
		assertThat(viewOf(roomName, "나").alive()).isFalse();

		Thread.sleep(1_000);
		assertThat(roomService.getPublicRoom(roomName).phase())
				.isEqualTo(caughtRole == Role.THIEF ? "ROUND_END" : "NIGHT");
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
		Thread.sleep(1_300); // 최종 투표 종료 → 결과
		Thread.sleep(1_000); // 결과 → 라운드 종료

		var roundEnd = roomService.getPublicRoom(roomName);
		assertThat(roundEnd.phase()).isEqualTo("ROUND_END");
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

		Thread.sleep(1_300);
		var roundEnd = roomService.getPublicRoom(roomName);
		assertThat(roundEnd.phase()).isEqualTo("ROUND_END");
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
		Thread.sleep(1_300);
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("NIGHT");
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

		Thread.sleep(1_000);
		var nextTurn = roomService.getPublicRoom(roomName);
		assertThat(nextTurn.phase()).isEqualTo("DISCUSSION");
		assertThat(nextTurn.turn()).isEqualTo(2);
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

		Thread.sleep(1_000);
		var result = roomService.getPublicRoom(roomName);
		assertThat(result.phase()).isEqualTo("RESULT");
		assertThat(result.voteResult().kind()).isEqualTo("THIEF_KILLED");
		assertThat(result.voteResult().nicknames()).containsExactly(nicknameOf(thief));
		assertThat(roomService.walletOf(citizen).gold()).isEqualTo(citizenGold + 1);

		Thread.sleep(1_000);
		var roundEnd = roomService.getPublicRoom(roomName);
		assertThat(roundEnd.phase()).isEqualTo("ROUND_END");
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
		Thread.sleep(1_300); // 최종 투표 종료 → 결과
		Thread.sleep(1_000); // 결과 → 라운드 종료
		assertThat(roomService.getPublicRoom(roomName).phase()).isEqualTo("ROUND_END");

		// 검거 +1G, 라운드 종료 3G(탐관오리는 10G), 칸 +8G, 스피드런(3턴 이내) +7G
		assertThat(roomService.walletOf(kan).gold()).isEqualTo(60 + 1 + 8 + 3);
		assertThat(roomService.walletOf(miser).gold()).isEqualTo(60 + 1 + 10);
		assertThat(roomService.walletOf(hunter).gold()).isEqualTo(60 + 1 + 7 + 3);
		assertThat(roomService.walletOf(thief).gold()).isEqualTo(60 + 3);
	}
}
