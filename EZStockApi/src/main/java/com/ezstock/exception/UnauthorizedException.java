package com.ezstock.exception;

/** 沒登入、帳密錯誤、token 無效，回應 401 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
