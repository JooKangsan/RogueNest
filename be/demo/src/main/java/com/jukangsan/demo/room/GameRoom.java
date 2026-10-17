package com.jukangsan.demo.room;

import java.util.LinkedHashMap;
import java.util.Map;

final class GameRoom {
	static final int MAX_PLAYERS = 12;

	final String code;
	final String passwordSalt;
	final String passwordHash;
	final Map<String, Player> players = new LinkedHashMap<>();
	String hostPlayerId;
	String phase = "WAITING";
	RoomConfig config;

	GameRoom(String code, String passwordSalt, String passwordHash, RoomConfig config) {
		this.code = code;
		this.passwordSalt = passwordSalt;
		this.passwordHash = passwordHash;
		this.config = config;
	}

	static final class Player {
		final String id;
		final String nickname;
		boolean ready;

		Player(String id, String nickname) {
			this.id = id;
			this.nickname = nickname;
		}
	}
}
