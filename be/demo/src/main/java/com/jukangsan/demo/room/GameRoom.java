package com.jukangsan.demo.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

final class GameRoom {
	static final int MAX_PLAYERS = 12;
	static final String ABSTAIN = "ABSTAIN";

	String name;
	final String passwordSalt;
	final String passwordHash;
	final Map<String, Player> players = new LinkedHashMap<>();
	String hostPlayerId;
	String phase = "WAITING";
	RoomConfig config;
	// 게임 시작 시 서버가 뽑는 배경 번호. 대기 중에는 null.
	Integer backgroundIndex;
	int round;
	int turn;
	Instant phaseEndsAt;
	// 단계가 바뀔 때마다 증가한다. 예약된 단계 전환이 이미 지난 단계의 것이면 무시한다.
	long phaseVersion;
	ScheduledFuture<?> phaseTask;
	// 최종 투표(VOTE) 단계에서 광장 중앙에 선 플레이어 id. 2명 이상이면 이들에게만 투표할 수 있다.
	final List<String> trialPlayerIds = new ArrayList<>();
	// 직전 투표 결과. 결과 안내(RESULT) 단계에서 보여준다.
	RoomDtos.VoteResultView lastVoteResult;
	// 현재 단계가 끝나면 실행할 다음 단계. "다음 라운드" 버튼으로 기다리지 않고 바로 넘길 때 쓴다.
	Runnable phaseNext;
	// 라운드 종료 결과 (ROUND_END / GAME_OVER 단계에서 보여준다)
	RoomDtos.RoundSummaryView roundSummary;
	// 지난밤 살인마에게 살해된 플레이어 닉네임. 다음 턴 시작 시 공개하고, 다음 밤이 오면 지운다.
	String nightVictim;

	GameRoom(String name, String passwordSalt, String passwordHash, RoomConfig config) {
		this.name = name;
		this.passwordSalt = passwordSalt;
		this.passwordHash = passwordHash;
		this.config = config;
	}

	static final class Player {
		final String id;
		String nickname;
		boolean ready;
		Role role;
		int gold;
		int coins;
		boolean alive = true;
		// 보유한 인물 카드 id (라운드마다 초기화). 공개 전에는 본인에게만 보낸다.
		String cardId;
		// 보유 카드를 공개했는지. 공개는 그 라운드 동안 되돌릴 수 없고, 카드를 새로 뽑으면 새 카드는 비공개.
		boolean cardRevealed;
		// 이번 턴 투표. null=아직 안 함, ABSTAIN=기권, 그 외=대상 플레이어 id
		String vote;
		// 살인마가 이번 밤에 고른 살해 대상 플레이어 id. 밤이 끝날 때 실행한다.
		String killTarget;

		Player(String id, String nickname) {
			this.id = id;
			this.nickname = nickname;
		}
	}
}
