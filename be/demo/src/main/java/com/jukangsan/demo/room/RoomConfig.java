package com.jukangsan.demo.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoomConfig(
		@Min(3) @Max(10) int roundCount,
		@Min(20) @Max(100) int coinPrice,
		@Min(60) @Max(180) int turnDurationSeconds
) {
}
