package com.postiva.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.reactive.function.client.WebClientException;
import com.postiva.social.client.MetaApiException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String META_UNAVAILABLE =
            "Facebook, Instagram hoặc Threads chưa phản hồi. Bạn thử lại sau ít phút; nếu vẫn lỗi, vào Kết nối kênh để kết nối lại.";

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return ApiResponse.fail("Thông tin gửi lên không hợp lệ. Bạn tải lại trang rồi thử lại.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Dữ liệu không hợp lệ");
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse("Dữ liệu không hợp lệ");
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ApiResponse.fail("Thông tin gửi lên không hợp lệ. Bạn tải lại trang rồi thử lại.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ApiResponse.fail("Thông tin này đã có hoặc vừa được thay đổi ở nơi khác. Bạn tải lại trang rồi thử lại.");
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleMetaError(WebClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(META_UNAVAILABLE));
    }

    @ExceptionHandler(MetaApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleMetaApiError(MetaApiException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(META_UNAVAILABLE));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(ApiResponse.fail(
                        exception.getReason() == null || exception.getReason().isBlank()
                                ? "Không thể xử lý yêu cầu"
                                : exception.getReason()
                ));
    }
}
