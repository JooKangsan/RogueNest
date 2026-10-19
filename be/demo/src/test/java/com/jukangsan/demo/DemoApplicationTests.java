package com.jukangsan.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.jukangsan.demo.meta.CardCatalog;
import com.jukangsan.demo.room.RoomConfig;
import com.jukangsan.demo.room.RoomService;
import jakarta.validation.Validator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "game.room.disconnect-grace-seconds=1")
class DemoApplicationTests {
	private static final Pattern ROOM_NAME = Pattern.compile("\"roomName\":\"([^\"]+)\"");
	// HTTP/2 업그레이드(h2c) 시도 없이 HTTP/1.1로만 요청한다. 업그레이드 중 연결이 끊기면 응답 없이 실패할 수 있다.
	private final HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.build();

	@Autowired
	private CardCatalog cardCatalog;

	@Autowired
	private RoomService roomService;

	@Autowired
	private Validator validator;

	@LocalServerPort
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void createsRoomAndIssuesSessionForValidJoin() throws Exception {
		HttpResponse<String> created = post("/api/rooms",
				"{\"roomName\":\"First Lobby\",\"password\":\"room-secret\"}");
		assertThat(created.statusCode()).isEqualTo(200);
		Matcher matcher = ROOM_NAME.matcher(created.body());
		assertThat(matcher.find()).isTrue();
		String roomName = matcher.group(1);

		HttpResponse<String> joined = post("/api/rooms/" + roomName.replace(" ", "%20") + "/join",
				"{\"nickname\":\"도적\",\"password\":\"room-secret\"}");
		assertThat(joined.statusCode()).isEqualTo(200);
		assertThat(joined.body()).contains("\"nickname\":\"도적\"", "\"sessionToken\"");
		assertThat(joined.body()).doesNotContain("\"playerId\"");

		HttpResponse<String> room = get("/api/rooms/" + roomName.replace(" ", "%20"));
		assertThat(room.body()).contains("\"phase\":\"WAITING\"", "\"nickname\":\"도적\"", "\"roomName\":\"First Lobby\"");
		assertThat(room.body()).doesNotContain("\"playerId\"", "\"hostPlayerId\"");
	}

	@Test
	void rejectsJoinWithWrongPassword() throws Exception {
		HttpResponse<String> created = post("/api/rooms",
				"{\"roomName\":\"Password Lobby\",\"password\":\"room-secret\"}");
		Matcher matcher = ROOM_NAME.matcher(created.body());
		assertThat(matcher.find()).isTrue();
		String roomName = matcher.group(1);

		HttpResponse<String> joined = post("/api/rooms/" + roomName.replace(" ", "%20") + "/join",
				"{\"nickname\":\"도적\",\"password\":\"wrong-secret\"}");

		assertThat(joined.statusCode()).isEqualTo(401);
		assertThat(joined.body()).contains("\"code\":\"INVALID_PASSWORD\"");
	}

	@Test
	void publishesCardCatalogWithFortyEightCardsAndThirteenSynergies() throws Exception {
		HttpResponse<String> response = get("/api/meta/cards");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"cards\"", "\"synergies\"", "\"후열의밤\"");
		assertThat(cardCatalog.cards()).hasSize(48);
		assertThat(cardCatalog.synergies()).hasSize(13);
	}

	@Test
	void validatesRoomSettingsRangesAndFixedTurnDuration() {
		assertThat(validator.validate(new RoomConfig(3, 25, 120))).isEmpty();
		assertThat(validator.validate(new RoomConfig(11, 75, 120))).isEmpty();
		assertThat(validator.validate(new RoomConfig(2, 50, 120))).isNotEmpty();
		assertThat(validator.validate(new RoomConfig(12, 50, 120))).isNotEmpty();
		assertThat(validator.validate(new RoomConfig(7, 24, 120))).isNotEmpty();
		assertThat(validator.validate(new RoomConfig(7, 76, 120))).isNotEmpty();
		assertThat(validator.validate(new RoomConfig(7, 50, 119))).isNotEmpty();
		assertThat(validator.validate(new RoomConfig(7, 50, 121))).isNotEmpty();
	}

	@Test
	void listsWaitingRoomsWithPlayersAndShowsPasswordRequirement() throws Exception {
		String lockedName = roomService.createRoom("Locked Lobby", "room-secret");
		String emptyName = roomService.createRoom("Empty Lobby", null);
		roomService.joinRoom(lockedName, "방장", "room-secret");

		HttpResponse<String> response = get("/api/rooms");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains(lockedName, "\"passwordRequired\":true", "\"playerCount\":1");
		assertThat(response.body()).doesNotContain(emptyName, "방장", "sessionToken");
	}

	@Test
	void rejectsDuplicateRoomNamesIgnoringCaseAndRepeatedSpaces() throws Exception {
		assertThat(post("/api/rooms", "{\"roomName\":\"Same  Lobby\"}").statusCode()).isEqualTo(200);

		HttpResponse<String> duplicate = post("/api/rooms", "{\"roomName\":\" same lobby \"}");

		assertThat(duplicate.statusCode()).isEqualTo(409);
		assertThat(duplicate.body()).contains("\"code\":\"ROOM_NAME_TAKEN\"");
	}

	@Test
	void createsAndJoinsRoomByKoreanName() throws Exception {
		String encodedRoomName = "%EC%9A%B0%EB%A6%AC%20%EB%AA%A8%EC%9E%84";
		HttpResponse<String> created = post("/api/rooms", "{\"roomName\":\"우리 모임\"}");

		assertThat(created.statusCode()).isEqualTo(200);
		assertThat(created.body()).contains("\"roomName\":\"우리 모임\"");
		assertThat(post("/api/rooms/" + encodedRoomName + "/join", "{\"nickname\":\"참가자\"}").statusCode())
				.isEqualTo(200);
		assertThat(get("/api/rooms/" + encodedRoomName).body()).contains("\"roomName\":\"우리 모임\"");
	}

	@Test
	void removesDisconnectedHostAfterGracePeriodAndTransfersHost() throws Exception {
		String roomName = roomService.createRoom("Disconnect Lobby", null);
		var host = roomService.joinRoom(roomName, "방장", null);
		var guest = roomService.joinRoom(roomName, "참가자", null);
		RoomService.PlayerSession hostSession = roomService.authenticate(host.sessionToken());

		roomService.connected(hostSession, "host-socket");
		roomService.disconnect(hostSession.playerId(), "host-socket");

		assertThat(roomService.getPublicRoom(roomName).players()).hasSize(2);
		Thread.sleep(1_200);

		var room = roomService.getPublicRoom(roomName);
		assertThat(room.players()).hasSize(1);
		assertThat(room.players().getFirst().nickname()).isEqualTo("참가자");
		assertThat(room.players().getFirst().host()).isTrue();
		assertThatThrownBy(() -> roomService.authenticate(host.sessionToken()))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
	}

	@Test
	void reconnectingDuringGracePeriodCancelsPlayerRemoval() throws Exception {
		String roomName = roomService.createRoom("Reconnect Lobby", null);
		var joined = roomService.joinRoom(roomName, "재접속", null);
		RoomService.PlayerSession session = roomService.authenticate(joined.sessionToken());

		roomService.connected(session, "old-socket");
		roomService.disconnect(session.playerId(), "old-socket");
		roomService.connected(session, "new-socket");
		Thread.sleep(1_200);

		assertThat(roomService.getPublicRoom(roomName).players()).hasSize(1);
		assertThat(roomService.authenticate(joined.sessionToken()).playerId()).isEqualTo(session.playerId());
	}

	@Test
	void closingOneOfMultipleConnectionsDoesNotStartDisconnectGracePeriod() throws Exception {
		String roomName = roomService.createRoom("Multiple Lobby", null);
		var joined = roomService.joinRoom(roomName, "다중 접속", null);
		RoomService.PlayerSession session = roomService.authenticate(joined.sessionToken());

		roomService.connected(session, "socket-one");
		roomService.connected(session, "socket-two");
		roomService.disconnect(session.playerId(), "socket-one");
		Thread.sleep(1_200);

		assertThat(roomService.getPublicRoom(roomName).players()).hasSize(1);
	}

	@Test
	void leavingRemovesPlayerImmediatelyAndTransfersHost() throws Exception {
		String roomName = roomService.createRoom("Leave Lobby", null);
		var host = roomService.joinRoom(roomName, "방장", null);
		roomService.joinRoom(roomName, "참가자", null);
		RoomService.PlayerSession hostSession = roomService.authenticate(host.sessionToken());
		roomService.connected(hostSession, "host-socket");

		roomService.leave(hostSession, roomName);
		roomService.disconnect(hostSession.playerId(), "host-socket");

		var room = roomService.getPublicRoom(roomName);
		assertThat(room.players()).hasSize(1);
		assertThat(room.players().getFirst().host()).isTrue();
		assertThatThrownBy(() -> roomService.authenticate(host.sessionToken()))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
	}

	@Test
	void leavingLastPlayerDeletesRoom() throws Exception {
		String roomName = roomService.createRoom("Last Leave Lobby", null);
		var joined = roomService.joinRoom(roomName, "혼자", null);

		roomService.leave(roomService.authenticate(joined.sessionToken()), roomName);

		assertThatThrownBy(() -> roomService.getPublicRoom(roomName))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
		assertThat(get("/api/rooms").body()).doesNotContain(roomName);
	}

	@Test
	void hostRenamesRoomAndSessionsFollowNewName() throws Exception {
		String roomName = roomService.createRoom("Old Name Lobby", null);
		var host = roomService.joinRoom(roomName, "방장", null);
		var guest = roomService.joinRoom(roomName, "참가자", null);
		roomService.createRoom("Taken Lobby", null);
		RoomService.PlayerSession hostSession = roomService.authenticate(host.sessionToken());
		RoomService.PlayerSession guestSession = roomService.authenticate(guest.sessionToken());

		assertThatThrownBy(() -> roomService.renameRoom(guestSession, roomName, "Guest Lobby"))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
		assertThatThrownBy(() -> roomService.renameRoom(hostSession, roomName, " taken  lobby "))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);

		roomService.renameRoom(hostSession, roomName, "New Name Lobby");

		assertThat(roomService.getPublicRoom("New Name Lobby").players()).hasSize(2);
		assertThatThrownBy(() -> roomService.getPublicRoom(roomName))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
		assertThat(roomService.authenticate(guest.sessionToken()).roomName()).isEqualTo("New Name Lobby");
		assertThat(roomService.authenticateByPlayerId(guestSession.playerId()).roomName())
				.isEqualTo("New Name Lobby");
		assertThat(get("/api/rooms").body()).contains("New Name Lobby").doesNotContain("Old Name Lobby");
	}

	@Test
	void playerChangesNicknameButNotToDuplicate() throws Exception {
		String roomName = roomService.createRoom("Nickname Lobby", null);
		var first = roomService.joinRoom(roomName, "첫번째", null);
		roomService.joinRoom(roomName, "두번째", null);
		RoomService.PlayerSession session = roomService.authenticate(first.sessionToken());

		assertThatThrownBy(() -> roomService.changeNickname(session, roomName, "두번째"))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
		assertThatThrownBy(() -> roomService.changeNickname(session, roomName, " "))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);

		roomService.changeNickname(session, roomName, "새닉네임");

		assertThat(roomService.getPublicRoom(roomName).players())
				.extracting(player -> player.nickname())
				.containsExactly("새닉네임", "두번째");
	}

	@Test
	void startingGamePicksSharedBackgroundWithinConfiguredRange() throws Exception {
		String roomName = roomService.createRoom("Background Lobby", null);
		var host = roomService.joinRoom(roomName, "방장", null);
		for (String nickname : new String[] {"둘", "셋", "넷"}) {
			roomService.joinRoom(roomName, nickname, null);
		}
		assertThat(roomService.getPublicRoom(roomName).backgroundIndex()).isNull();

		roomService.startGame(roomService.authenticate(host.sessionToken()), roomName);

		var room = roomService.getPublicRoom(roomName);
		assertThat(room.phase()).isEqualTo("PREPARE");
		assertThat(room.backgroundIndex()).isBetween(0, 16);
	}

	private HttpResponse<String> post(String path, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}
}
