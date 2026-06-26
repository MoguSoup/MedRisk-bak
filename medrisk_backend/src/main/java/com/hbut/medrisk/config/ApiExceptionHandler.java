package com.hbut.medrisk.config;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.service.AuthException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiResponse<Void> auth(AuthException ex) {
        return ApiResponse.fail(401, ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ApiResponse<Void> forbidden(SecurityException ex) {
        return ApiResponse.fail(403, ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiResponse<Void> notFound(EntityNotFoundException ex) {
        return ApiResponse.fail(404, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> invalid(MethodArgumentNotValidException ex) {
        return ApiResponse.fail(400, "请求参数不完整或格式不正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> badRequest(IllegalArgumentException ex) {
        return ApiResponse.fail(400, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> dataIntegrity(DataIntegrityViolationException ex) {
        return ApiResponse.fail(400, "数据已被业务记录引用，请改为禁用或调整关联后再操作");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiResponse<Void> error(Exception ex) {
        return ApiResponse.fail(500, "系统暂时不可用，请稍后重试");
    }
}
