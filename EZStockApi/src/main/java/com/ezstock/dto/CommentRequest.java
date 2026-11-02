package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "請輸入留言")
        @Size(max = 1000, message = "留言最多 1000 個字")
        String content) {

    @Override
    public String toString() {
        return "CommentRequest[length=" + (content == null ? 0 : content.length()) + "]";
    }
}
