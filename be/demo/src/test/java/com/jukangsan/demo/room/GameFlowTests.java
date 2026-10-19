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
		"game.economy.start-gold=60"
})
class GameFlowTests {
	@Autowired
	private RoomService roomService;

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
}
