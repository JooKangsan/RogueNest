package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.CreateRoomRequest;
import com.jukangsan.demo.room.RoomDtos.CreateRoomResponse;
import com.jukangsan.demo.room.RoomDtos.JoinRoomRequest;
import com.jukangsan.demo.room.RoomDtos.JoinRoomResponse;
import com.jukangsan.demo.room.RoomDtos.RoomSummary;
import com.jukangsan.demo.room.RoomDtos.RoomView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/rooms")
public class RoomController {
	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping
	@Operation(summary = "방 생성", description = "선택적으로 비밀번호가 설정된 새 대기실을 생성합니다.")
	public CreateRoomResponse createRoom(@Valid @RequestBody(required = false) CreateRoomRequest request) {
		if (request == null) {
			throw new RoomException(org.springframework.http.HttpStatus.BAD_REQUEST,
					"INVALID_ROOM_NAME", "방 이름을 입력해 주세요.");
		}
		return new CreateRoomResponse(roomService.createRoom(request.roomName(), request.password()));
	}

	@GetMapping
	@Operation(summary = "대기실 목록", description = "입장 가능한 대기 중인 방 목록을 반환합니다.")
	public List<RoomSummary> getRooms() {
		return roomService.getWaitingRooms();
	}

	@PostMapping("/{roomName}/join")
	@Operation(summary = "방 입장", description = "닉네임과 비밀번호를 검증하고 임시 세션 토큰을 발급합니다.")
	public JoinRoomResponse joinRoom(
			@PathVariable String roomName,
			@Valid @RequestBody JoinRoomRequest request
	) {
		return roomService.joinRoom(roomName, request.nickname(), request.password());
	}

	@GetMapping("/{roomName}")
	@Operation(summary = "방 정보 조회", description = "방 입장 전 확인할 수 있는 공개 대기실 정보를 반환합니다.")
	public RoomView getRoom(@PathVariable String roomName) {
		return roomService.getPublicRoom(roomName);
	}
}
