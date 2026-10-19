package com.jukangsan.demo.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoomConfig(
		@Min(3) @Max(11) int roundCount,
		@Min(25) @Max(75) int coinPrice,
		@Min(120) @Max(120) int turnDurationSeconds
) {
}
