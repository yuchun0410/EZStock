package com.ezstock.exception;

/** 使用者送來的參數不合法，回應 400 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
