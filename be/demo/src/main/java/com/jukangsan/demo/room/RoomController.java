package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.CreateRoomRequest;
import com.jukangsan.demo.room.RoomDtos.CreateRoomResponse;
import com.jukangsan.demo.room.RoomDtos.JoinRoomRequest;
import com.jukangsan.demo.room.RoomDtos.JoinRoomResponse;
import com.jukangsan.demo.room.RoomDtos.RoomView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
		String password = request == null ? null : request.password();
		return new CreateRoomResponse(roomService.createRoom(password));
	}

	@PostMapping("/{code}/join")
	@Operation(summary = "방 입장", description = "닉네임과 비밀번호를 검증하고 임시 세션 토큰을 발급합니다.")
	public JoinRoomResponse joinRoom(
			@PathVariable String code,
			@Valid @RequestBody JoinRoomRequest request
	) {
		return roomService.joinRoom(code, request.nickname(), request.password());
	}

	@GetMapping("/{code}")
	@Operation(summary = "방 정보 조회", description = "방 입장 전 확인할 수 있는 공개 대기실 정보를 반환합니다.")
	public RoomView getRoom(@PathVariable String code) {
		return roomService.getPublicRoom(code);
	}
}
