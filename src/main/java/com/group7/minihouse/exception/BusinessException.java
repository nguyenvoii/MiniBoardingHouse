package com.group7.minihouse.exception;

import org.springframework.http.HttpStatus;

/** Loi nghiep vu: nem ra tu service, GlobalExceptionHandler se doi thanh response chuan. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(HttpStatus.NOT_FOUND, what + " khong ton tai");
    }

    public HttpStatus getStatus() { return status; }
}
