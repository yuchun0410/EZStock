package com.ezstock.dto;

/**
 * 按讚或收回讚之後的結果，前端用來校正畫面（前端會先樂觀更新）。
 *
 * @param liked     目前是不是按讚中
 * @param likeCount 最新的讚數
 */
public record LikeResponse(boolean liked, int likeCount) {
}
