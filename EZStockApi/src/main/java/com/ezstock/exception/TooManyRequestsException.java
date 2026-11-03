package com.ezstock.exception;

/** 請求太頻繁（例如登入失敗太多次），回應 429，並用 Retry-After 告訴對方幾秒後可以再試 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
