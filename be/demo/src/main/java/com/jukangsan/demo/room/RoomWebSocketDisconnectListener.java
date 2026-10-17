package com.jukangsan.demo.room;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class RoomWebSocketDisconnectListener {
	private final RoomService roomService;

	public RoomWebSocketDisconnectListener(RoomService roomService) {
		this.roomService = roomService;
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		Principal principal = accessor.getUser();
		if (principal != null && accessor.getSessionId() != null) {
			roomService.disconnect(principal.getName(), accessor.getSessionId());
		}
	}
}
