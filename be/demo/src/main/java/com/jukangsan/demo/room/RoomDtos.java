package com.jukangsan.demo.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RoomDtos {
	private RoomDtos() {
	}

	public record CreateRoomRequest(@Size(max = 100) String password) {
	}

	public record CreateRoomResponse(String roomCode) {
	}

	public record JoinRoomRequest(
			@NotBlank @Size(max = 80) String nickname,
			@Size(max = 100) String password
	) {
	}

	public record JoinRoomResponse(String roomCode, String nickname, String sessionToken) {
	}

	public record PlayerView(String nickname, boolean host, boolean ready) {
	}

	public record RoomView(
			String roomCode,
			String phase,
			List<PlayerView> players,
			RoomConfig config
	) {
	}

	public record ChatRequest(@NotBlank @Size(max = 500) String message) {
	}

	public record ChatMessage(String nickname, String message, Instant sentAt) {
	}

	public record RoomEvent(String type, Object payload, Instant occurredAt) {
		public static RoomEvent of(String type, Object payload) {
			return new RoomEvent(type, payload, Instant.now());
		}
	}

	public record PrivateSnapshot(RoomView room, String nickname) {
	}

	public record ErrorResponse(String code, String message) {
	}

	public record SettingsRequest(@NotNull @Valid RoomConfig config) {
	}
}
