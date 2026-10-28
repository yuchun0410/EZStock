package com.ezstock.datasource.finmind;

/** 呼叫 FinMind 失敗（連線失敗、超過用量、回應格式錯誤等） */
public class FinMindApiException extends RuntimeException {

    public FinMindApiException(String message) {
        super(message);
    }

    public FinMindApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
