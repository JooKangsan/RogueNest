package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.ChatRequest;
import com.jukangsan.demo.room.RoomDtos.NicknameRequest;
import com.jukangsan.demo.room.RoomDtos.RenameRoomRequest;
import com.jukangsan.demo.room.RoomDtos.SettingsRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class RoomWebSocketController {
	private final RoomService roomService;

	public RoomWebSocketController(RoomService roomService) {
		this.roomService = roomService;
	}

	@MessageMapping("/rooms/{roomName}/snapshot")
	public void snapshot(@DestinationVariable String roomName, Principal principal) {
		roomService.sendSnapshot(session(principal), roomName);
	}

	@MessageMapping("/rooms/{roomName}/ready")
	public void ready(@DestinationVariable String roomName, boolean ready, Principal principal) {
		roomService.setReady(session(principal), roomName, ready);
	}

	@MessageMapping("/rooms/{roomName}/settings")
	public void settings(@DestinationVariable String roomName, @Valid SettingsRequest request, Principal principal) {
		roomService.updateSettings(session(principal), roomName, request.config());
	}

	@MessageMapping("/rooms/{roomName}/start")
	public void start(@DestinationVariable String roomName, Principal principal) {
		roomService.startGame(session(principal), roomName);
	}

	@MessageMapping("/rooms/{roomName}/chat")
	public void chat(@DestinationVariable String roomName, ChatRequest request, Principal principal) {
		roomService.sendChat(session(principal), roomName, request.message());
	}

	@MessageMapping("/rooms/{roomName}/rename")
	public void rename(@DestinationVariable String roomName, RenameRoomRequest request, Principal principal) {
		roomService.renameRoom(session(principal), roomName, request.roomName());
	}

	@MessageMapping("/rooms/{roomName}/nickname")
	public void nickname(@DestinationVariable String roomName, NicknameRequest request, Principal principal) {
		roomService.changeNickname(session(principal), roomName, request.nickname());
	}

	// 처리 중 거부된 요청은 요청한 사람에게만 사유를 돌려준다.
	@MessageExceptionHandler(RoomException.class)
	@SendToUser(destinations = "/queue/private", broadcast = false)
	public RoomDtos.RoomEvent handleRoomException(RoomException exception) {
		return RoomDtos.RoomEvent.of("error", new RoomDtos.ErrorResponse(exception.code(), exception.getMessage()));
	}

	@MessageMapping("/rooms/{roomName}/exchange")
	public void exchange(@DestinationVariable String roomName, Principal principal) {
		roomService.exchangeCoin(session(principal), roomName);
	}

	@MessageMapping("/rooms/{roomName}/leave")
	public void leave(@DestinationVariable String roomName, Principal principal) {
		roomService.leave(session(principal), roomName);
	}

	private RoomService.PlayerSession session(Principal principal) {
		if (principal == null) {
			throw new RoomException(org.springframework.http.HttpStatus.UNAUTHORIZED,
					"INVALID_SESSION", "유효하지 않은 세션입니다.");
		}
		return roomService.authenticateByPlayerId(principal.getName());
	}
}
