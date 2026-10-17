package com.jukangsan.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import com.jukangsan.demo.meta.CardCatalog;
import com.jukangsan.demo.room.RoomService;

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
	private static final Pattern ROOM_CODE = Pattern.compile("\"roomCode\":\"([A-Z0-9]+)\"");
	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Autowired
	private CardCatalog cardCatalog;

	@Autowired
	private RoomService roomService;

	@LocalServerPort
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void createsRoomAndIssuesSessionForValidJoin() throws Exception {
		HttpResponse<String> created = post("/api/rooms", "{\"password\":\"room-secret\"}");
		assertThat(created.statusCode()).isEqualTo(200);
		Matcher matcher = ROOM_CODE.matcher(created.body());
		assertThat(matcher.find()).isTrue();
		String code = matcher.group(1);

		HttpResponse<String> joined = post("/api/rooms/" + code + "/join",
				"{\"nickname\":\"도적\",\"password\":\"room-secret\"}");
		assertThat(joined.statusCode()).isEqualTo(200);
		assertThat(joined.body()).contains("\"nickname\":\"도적\"", "\"sessionToken\"");
		assertThat(joined.body()).doesNotContain("\"playerId\"");

		HttpResponse<String> room = get("/api/rooms/" + code);
		assertThat(room.body()).contains("\"phase\":\"WAITING\"", "\"nickname\":\"도적\"");
		assertThat(room.body()).doesNotContain("\"playerId\"", "\"hostPlayerId\"");
	}

	@Test
	void rejectsJoinWithWrongPassword() throws Exception {
		HttpResponse<String> created = post("/api/rooms", "{\"password\":\"room-secret\"}");
		Matcher matcher = ROOM_CODE.matcher(created.body());
		assertThat(matcher.find()).isTrue();
		String code = matcher.group(1);

		HttpResponse<String> joined = post("/api/rooms/" + code + "/join",
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
	void removesDisconnectedHostAfterGracePeriodAndTransfersHost() throws Exception {
		String code = roomService.createRoom(null);
		var host = roomService.joinRoom(code, "방장", null);
		var guest = roomService.joinRoom(code, "참가자", null);
		RoomService.PlayerSession hostSession = roomService.authenticate(host.sessionToken());

		roomService.connected(hostSession, "host-socket");
		roomService.disconnect(hostSession.playerId(), "host-socket");

		assertThat(roomService.getPublicRoom(code).players()).hasSize(2);
		Thread.sleep(1_200);

		var room = roomService.getPublicRoom(code);
		assertThat(room.players()).hasSize(1);
		assertThat(room.players().getFirst().nickname()).isEqualTo("참가자");
		assertThat(room.players().getFirst().host()).isTrue();
		assertThatThrownBy(() -> roomService.authenticate(host.sessionToken()))
				.isInstanceOf(com.jukangsan.demo.room.RoomException.class);
	}

	@Test
	void reconnectingDuringGracePeriodCancelsPlayerRemoval() throws Exception {
		String code = roomService.createRoom(null);
		var joined = roomService.joinRoom(code, "재접속", null);
		RoomService.PlayerSession session = roomService.authenticate(joined.sessionToken());

		roomService.connected(session, "old-socket");
		roomService.disconnect(session.playerId(), "old-socket");
		roomService.connected(session, "new-socket");
		Thread.sleep(1_200);

		assertThat(roomService.getPublicRoom(code).players()).hasSize(1);
		assertThat(roomService.authenticate(joined.sessionToken()).playerId()).isEqualTo(session.playerId());
	}

	@Test
	void closingOneOfMultipleConnectionsDoesNotStartDisconnectGracePeriod() throws Exception {
		String code = roomService.createRoom(null);
		var joined = roomService.joinRoom(code, "다중 접속", null);
		RoomService.PlayerSession session = roomService.authenticate(joined.sessionToken());

		roomService.connected(session, "socket-one");
		roomService.connected(session, "socket-two");
		roomService.disconnect(session.playerId(), "socket-one");
		Thread.sleep(1_200);

		assertThat(roomService.getPublicRoom(code).players()).hasSize(1);
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
