package com.ezstock.exception;

/** 已登入，但沒有權限做這件事（例如修改別人的文章），回應 403 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
