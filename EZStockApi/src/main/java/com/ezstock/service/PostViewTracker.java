package com.ezstock.service;

/** 判斷這次瀏覽要不要算進瀏覽數：同一個人一段時間內重複看同一篇，只算一次 */
public interface PostViewTracker {

    /**
     * @param viewerKey 代表「同一個人」的字串：有登入用會員 id，沒登入用 IP
     * @return true 代表這段時間內第一次看，要把瀏覽數 +1
     */
    boolean isFirstView(long postId, String viewerKey);
}
