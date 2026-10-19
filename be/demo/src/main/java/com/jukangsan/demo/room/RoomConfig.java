package com.jukangsan.demo.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoomConfig(
		@Min(3) @Max(11) int roundCount,
		@Min(25) @Max(75) int coinPrice,
		// 토론 시간(초). 고정값이며 기본값은 application.properties와 맞춘다.
		@Min(180) @Max(180) int turnDurationSeconds
) {
}
