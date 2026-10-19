package com.jukangsan.demo.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RoomDtos {
	private RoomDtos() {
	}

	public record CreateRoomRequest(
			@NotBlank @Size(max = 24) @Pattern(regexp = "[\\p{IsHangul}A-Za-z0-9 _-]+") String roomName,
			@Size(max = 100) String password
	) {
	}

	public record CreateRoomResponse(String roomName) {
	}

	public record JoinRoomRequest(
			@NotBlank @Size(max = 80) String nickname,
			@Size(max = 100) String password
	) {
	}

	public record JoinRoomResponse(String roomName, String nickname, String sessionToken) {
	}

	public record PlayerView(String nickname, boolean host, boolean ready) {
	}

	public record RoomView(
			String roomName,
			String phase,
			List<PlayerView> players,
			RoomConfig config,
			Integer backgroundIndex,
			int round,
			int turn,
			Instant phaseEndsAt,
			int minPlayers,
			int maxTurns
	) {
	}

	public record RoomSummary(
			String roomName,
			int playerCount,
			int maxPlayers,
			boolean passwordRequired
	) {
	}

	public record RenameRoomRequest(String roomName) {
	}

	public record NicknameRequest(String nickname) {
	}

	public record RoomRenamed(String roomName) {
	}

	public record NicknameChanged(String nickname) {
	}

	public record RoleView(Role role) {
	}

	// 본인에게만 보내는 골드/코인. 공개 코인 수는 라운드 종료 시점에만 따로 갱신한다.
	public record WalletView(int gold, int coins) {
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

	public record PrivateSnapshot(RoomView room, String nickname, Role role, WalletView wallet) {
	}

	public record ErrorResponse(String code, String message) {
	}

	public record SettingsRequest(@NotNull @Valid RoomConfig config) {
	}
}
