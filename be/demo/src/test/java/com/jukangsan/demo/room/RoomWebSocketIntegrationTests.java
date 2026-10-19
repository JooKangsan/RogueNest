package com.jukangsan.demo.room;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomWebSocketIntegrationTests {
	private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

	@Autowired
	private RoomService roomService;

	@Autowired
	@Qualifier("roomBrokerHeartbeatScheduler")
	private TaskScheduler heartbeatScheduler;

	@LocalServerPort
	private int port;

	@AfterEach
	void stopClient() {
		stompClient.stop();
	}

	@Test
	void authenticatesSubscribesAndDeliversPrivateSnapshotAndRoomUpdates() throws Exception {
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		stompClient.setTaskScheduler(heartbeatScheduler);
		stompClient.start();

		String roomName = roomService.createRoom("웹소켓 대기실", null);
		RoomDtos.JoinRoomResponse join = roomService.joinRoom(roomName, "websocket-client", null);
		CountDownLatch privateSnapshotReceived = new CountDownLatch(1);
		CountDownLatch roomUpdateReceived = new CountDownLatch(1);
		AtomicReference<Map<?, ?>> privateSnapshot = new AtomicReference<>();
		AtomicReference<Map<?, ?>> roomUpdate = new AtomicReference<>();
		AtomicReference<String> clientError = new AtomicReference<>();
		CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
				URI.create("ws://localhost:" + port + "/ws"),
				new WebSocketHttpHeaders(),
				connectHeaders(join.sessionToken()),
				new StompSessionHandlerAdapter() {
					@Override
					public void handleException(StompSession session,
												org.springframework.messaging.simp.stomp.StompCommand command,
												StompHeaders headers, byte[] payload, Throwable exception) {
						clientError.set(exception.getMessage());
					}

					@Override
					public void handleTransportError(StompSession session, Throwable exception) {
						clientError.set(exception.getMessage());
					}
				});

		StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
		StompSessionHandlerAdapter privateHandler = jsonFrameHandler(payload -> {
			privateSnapshot.set(payload);
			privateSnapshotReceived.countDown();
		});
		StompSessionHandlerAdapter roomHandler = jsonFrameHandler(payload -> {
			roomUpdate.set(payload);
			if (hasReadyPlayer(payload)) {
				roomUpdateReceived.countDown();
			}
		});
		session.subscribe("/topic/room/" + roomName, roomHandler);
		session.subscribe("/user/queue/private", privateHandler);
		Thread.sleep(250);
		session.send("/app/rooms/" + roomName + "/snapshot", Map.of());

		assertThat(privateSnapshotReceived.await(5, TimeUnit.SECONDS)).withFailMessage(
				"Private snapshot was not delivered. Client error: %s", clientError.get()).isTrue();
		assertThat(privateSnapshot.get().get("type")).isEqualTo("snapshot");
		assertThat(((Map<?, ?>) privateSnapshot.get().get("payload")).get("nickname"))
				.isEqualTo("websocket-client");

		session.send("/app/rooms/" + roomName + "/ready", true);
		assertThat(roomUpdateReceived.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(roomUpdate.get().get("type")).isEqualTo("room");
		assertThat(hasReadyPlayer(roomUpdate.get())).isTrue();
		session.disconnect();
	}

	private StompHeaders connectHeaders(String token) {
		StompHeaders headers = new StompHeaders();
		headers.add("x-session-token", token);
		return headers;
	}

	private StompSessionHandlerAdapter jsonFrameHandler(
			java.util.function.Consumer<Map<?, ?>> consumer
	) {
		return new StompSessionHandlerAdapter() {
			@Override
			public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				consumer.accept((Map<?, ?>) payload);
			}
		};
	}

	private boolean hasReadyPlayer(Map<?, ?> event) {
		Object payload = event.get("payload");
		if (!(payload instanceof Map<?, ?> room)) {
			return false;
		}
		Object players = room.get("players");
		if (!(players instanceof List<?> list)) {
			return false;
		}
		for (Object value : list) {
			if (value instanceof Map<?, ?> player && Boolean.TRUE.equals(player.get("ready"))) {
				return true;
			}
		}
		return false;
	}
}
