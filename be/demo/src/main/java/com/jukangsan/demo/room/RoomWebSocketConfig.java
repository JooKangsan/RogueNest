package com.jukangsan.demo.room;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class RoomWebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000;

	private final RoomService roomService;
	private final TaskScheduler brokerTaskScheduler;

	public RoomWebSocketConfig(
			RoomService roomService,
			@Qualifier("roomBrokerHeartbeatScheduler") TaskScheduler brokerTaskScheduler
	) {
		this.roomService = roomService;
		this.brokerTaskScheduler = brokerTaskScheduler;
	}

	@Bean
	public static ThreadPoolTaskScheduler roomBrokerHeartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("stomp-heartbeat-");
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue")
				.setTaskScheduler(brokerTaskScheduler)
				.setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS});
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		registry.setPreservePublishOrder(true);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws");
		// 한 연결에서 온 메시지는 보낸 순서대로 처리한다.
		// 클라이언트는 개인 큐 구독 직후 스냅샷을 요청하므로, 순서가 뒤바뀌면 스냅샷을 받지 못한다.
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor =
						MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor == null || accessor.getCommand() == null) {
					return message;
				}

				if (accessor.getCommand() == StompCommand.CONNECT) {
					String token = accessor.getFirstNativeHeader("x-session-token");
					if (token == null || token.isBlank()) {
						throw new RoomException(org.springframework.http.HttpStatus.UNAUTHORIZED,
								"INVALID_SESSION", "세션 토큰이 필요합니다.");
					}
					RoomService.PlayerSession session = roomService.authenticate(token);
					accessor.setUser((Principal) session::playerId);
					roomService.connected(session, accessor.getSessionId());
				} else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
					RoomService.PlayerSession session = authenticatedSession(accessor);
					String destination = accessor.getDestination();
					if (destination != null && destination.startsWith("/topic/room/")) {
						roomService.assertRoomSubscription(session, destination.substring("/topic/room/".length()));
					} else if (!"/user/queue/private".equals(destination)) {
						throw new RoomException(org.springframework.http.HttpStatus.FORBIDDEN,
								"SUBSCRIPTION_DENIED", "이 구독 경로에는 접근할 수 없습니다.");
					}
				} else if (accessor.getCommand() == StompCommand.SEND) {
					RoomService.PlayerSession session = authenticatedSession(accessor);
					String destination = accessor.getDestination();
					if (destination == null || !destination.startsWith("/app/rooms/")) {
						throw new RoomException(org.springframework.http.HttpStatus.FORBIDDEN,
								"MESSAGE_DENIED", "이 메시지 경로에는 접근할 수 없습니다.");
					}
					String remainder = destination.substring("/app/rooms/".length());
					int separator = remainder.indexOf('/');
					if (separator < 1) {
						throw new RoomException(org.springframework.http.HttpStatus.BAD_REQUEST,
								"INVALID_DESTINATION", "메시지 경로가 올바르지 않습니다.");
					}
					roomService.assertRoomSubscription(session, remainder.substring(0, separator));
				}
				return message;
			}
		});
	}

	private RoomService.PlayerSession authenticatedSession(StompHeaderAccessor accessor) {
		Principal principal = accessor.getUser();
		if (principal == null) {
			throw new RoomException(org.springframework.http.HttpStatus.UNAUTHORIZED,
					"INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		return roomService.authenticateByPlayerId(principal.getName());
	}
}
