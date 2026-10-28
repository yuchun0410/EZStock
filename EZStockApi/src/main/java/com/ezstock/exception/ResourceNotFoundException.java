package com.ezstock.exception;

/** 查無資料，回應 404 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
