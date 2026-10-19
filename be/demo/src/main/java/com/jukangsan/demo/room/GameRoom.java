package com.jukangsan.demo.room;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

final class GameRoom {
	static final int MAX_PLAYERS = 12;

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

		Player(String id, String nickname) {
			this.id = id;
			this.nickname = nickname;
		}
	}
}
