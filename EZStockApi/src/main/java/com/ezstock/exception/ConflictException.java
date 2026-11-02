package com.ezstock.exception;

/** 資料已存在（例如 Email 已經註冊過），回應 409 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
