package com.jukangsan.demo.room;

import com.jukangsan.demo.room.RoomDtos.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RoomExceptionHandler {
	@ExceptionHandler(RoomException.class)
	public ResponseEntity<ErrorResponse> handleRoomException(RoomException exception) {
		return ResponseEntity.status(exception.status())
				.body(new ErrorResponse(exception.code(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		FieldError error = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
		String field = error == null ? "요청" : error.getField();
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", field + " 값이 올바르지 않습니다."));
	}

	@ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
	}

}
