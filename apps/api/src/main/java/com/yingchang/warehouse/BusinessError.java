package com.yingchang.warehouse;

public class BusinessError extends RuntimeException {
    final String code;
    final int status;
    final Object details;
    public BusinessError(String code, String message) { this(code, message, 409, null); }
    public BusinessError(String code, String message, int status) { this(code, message, status, null); }
    public BusinessError(String code, String message, int status, Object details) {
        super(message); this.code = code; this.status = status; this.details = details;
    }
}
