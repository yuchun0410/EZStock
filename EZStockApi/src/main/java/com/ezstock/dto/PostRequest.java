package com.ezstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 發文或修改文章。
 *
 * @param stockIds 標記的股票，最多 5 檔；可以不標
 */
public record PostRequest(
        @NotBlank(message = "請輸入標題")
        @Size(max = 100, message = "標題最多 100 個字")
        String title,

        @NotBlank(message = "請輸入內容")
        @Size(max = 10000, message = "內容最多 10000 個字")
        String content,

        @Size(max = 5, message = "最多標記 5 檔股票")
        List<@NotBlank(message = "股票代號不能是空的") String> stockIds) {

    /** 不印內容（可能很長，也可能有私人資訊） */
    @Override
    public String toString() {
        return "PostRequest[titleLength=" + (title == null ? 0 : title.length())
                + ", contentLength=" + (content == null ? 0 : content.length()) + ", stockIds=" + stockIds + "]";
    }
}
