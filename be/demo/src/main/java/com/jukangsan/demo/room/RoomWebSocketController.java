package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.ChatRequest;
import com.jukangsan.demo.room.RoomDtos.SettingsRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class RoomWebSocketController {
	private final RoomService roomService;

	public RoomWebSocketController(RoomService roomService) {
		this.roomService = roomService;
	}

	@MessageMapping("/rooms/{code}/snapshot")
	public void snapshot(@DestinationVariable String code, Principal principal) {
		roomService.sendSnapshot(session(principal), code);
	}

	@MessageMapping("/rooms/{code}/ready")
	public void ready(@DestinationVariable String code, boolean ready, Principal principal) {
		roomService.setReady(session(principal), code, ready);
	}

	@MessageMapping("/rooms/{code}/settings")
	public void settings(@DestinationVariable String code, @Valid SettingsRequest request, Principal principal) {
		roomService.updateSettings(session(principal), code, request.config());
	}

	@MessageMapping("/rooms/{code}/start")
	public void start(@DestinationVariable String code, Principal principal) {
		roomService.startGame(session(principal), code);
	}

	@MessageMapping("/rooms/{code}/chat")
	public void chat(@DestinationVariable String code, ChatRequest request, Principal principal) {
		roomService.sendChat(session(principal), code, request.message());
	}

	private RoomService.PlayerSession session(Principal principal) {
		if (principal == null) {
			throw new RoomException(org.springframework.http.HttpStatus.UNAUTHORIZED,
					"INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		return roomService.authenticateByPlayerId(principal.getName());
	}
}
