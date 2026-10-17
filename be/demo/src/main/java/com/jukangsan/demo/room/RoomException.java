package com.jukangsan.demo.room;

import org.springframework.http.HttpStatus;

public class RoomException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	public RoomException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}
}
