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

	// voted: 이번 턴 투표(기권 포함)를 마쳤는지, votes: 이번 턴 받은 표 수.
	// 누가 누구를 찍었는지는 공개하지 않는다.
	// revealedCardId: 공개한 인물 카드. 비공개면 null
	// inLobby: 대기실에 들어와 있는지 (게임 종료 후 "다시하기"를 누른 사람만 true)
	public record PlayerView(String nickname, boolean host, boolean ready, boolean voted, int votes,
			boolean alive, boolean onTrial, String revealedCardId, boolean inLobby) {
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
			int maxTurns,
			VoteResultView voteResult,
			RoundSummaryView roundSummary,
			// 지난밤 살인마에게 살해된 플레이어. 없으면 null
			String nightVictim,
			// 장마로 강탈과 살인마 행동이 막힌 남은 턴 수
			int rainTurns,
			// 발동 중인 시너지와 참여자. 조합은 공개하고 효과는 공개하지 않는다.
			List<ActiveSynergyView> synergies,
			// 게임 종료 화면에서 최종 순위가 공개됐는지
			boolean rankingShown
	) {
	}

	// 발동 중인 시너지. 발동한 시너지만 지금 단계의 효과(effect)를 공개한다.
	public record ActiveSynergyView(String name, String effect, List<String> nicknames) {
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

	// 투표 결과. kind: THIEF_CAUGHT / MURDERER_CAUGHT / CITIZEN_DIED / NO_ONE / THIEF_LEFT(도적 탈주 = 검거 처리)
	//   / THIEF_KILLED(살인마가 밤에 도적을 처치) / THIEF_ASSASSINATED(늑대인간이 도적을 암살)
	//   / WITCH(마녀가 라운드를 끝냄)
	// 시민이 투표로 죽은 경우 직업은 공개하지 않으므로 kind만으로 구분한다.
	public record VoteResultView(String kind, List<String> nicknames) {
	}

	// tier: COMMON / RARE / LEGENDARY
	public record DrawCardRequest(String tier) {
	}

	// 본인 보유 인물 카드. 없으면 cardId=null. activated: 이번 라운드에 카드 사용 효과를 썼는지
	// bonusRerollTier: 시너지로 받은 무료 다시 뽑기 등급, freeDraw: 다음 뽑기 1회 무료
	public record MyCardView(String cardId, boolean activated, String bonusRerollTier, boolean freeDraw) {
	}

	// 라운드 종료 결과. 라운드가 끝나면 직업·골드·코인을 모두 공개한다.
	// thiefCaught: 도적 검거 여부(도적은 미검거 시 승리, 시민·살인마는 검거 시 승리)
	// finalRound: 마지막 라운드(게임 종료)인지
	public record RoundSummaryView(int round, boolean thiefCaught, boolean finalRound,
			List<RoundResultRow> rows) {
	}

	public record RoundResultRow(String nickname, Role role, int gold, int coins) {
	}

	public record VoteRequest(String target, boolean abstain) {
	}

	// 살인마 밤 행동. target=대상 닉네임, null이면 취소
	public record KillRequest(String target) {
	}

	// 인물 카드 사용. 대상을 골라야 하는 카드만 target(닉네임)을 채운다.
	public record ActivateCardRequest(String target) {
	}

	// 방 전체 알림 (늑대인간 암살 등)
	public record AnnouncementView(String message) {
	}

	// 본인이 고른 살해 대상 닉네임. 없으면 null
	public record MyKillTargetView(String target) {
	}

	// 본인에게만 크게 띄우는 모달 알림 (밤 사이 강탈 결과 등). tone: LOSS(잃음) / GAIN(얻음) / KILL(살해함) / DEATH(살해당함) / INFO
	public record AlertView(String tone, String title, List<String> lines) {
	}

	// 본인에게만 보내는 알림 (강탈 결과 등)
	public record NoticeView(String message) {
	}

	// 본인 투표 결과. target=대상 닉네임, abstain=기권. 둘 다 비어 있으면 아직 투표 전.
	public record MyVoteView(String target, boolean abstain) {
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

	public record PrivateSnapshot(RoomView room, String nickname, Role role, WalletView wallet,
			MyVoteView myVote, MyCardView myCard, MyKillTargetView myKillTarget) {
	}

	public record ErrorResponse(String code, String message) {
	}

	public record SettingsRequest(@NotNull @Valid RoomConfig config) {
	}
}
