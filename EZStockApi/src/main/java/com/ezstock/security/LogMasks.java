package com.ezstock.security;

/**
 * 寫進 log 前把個資遮起來。
 */
public final class LogMasks {

    private LogMasks() {
    }

    /** abcdef@gmail.com → a*****@gmail.com，看得出是哪個網域、方便除錯，但認不出是誰 */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "(空)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "*".repeat(Math.max(at - 1, 1)) + email.substring(at);
    }
}
