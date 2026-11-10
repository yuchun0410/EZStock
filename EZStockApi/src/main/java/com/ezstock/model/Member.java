package com.ezstock.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 會員。
 * 用靜態工廠方法建立，名稱就說明是哪一種註冊方式；密碼雜湊不提供 getter 以外的出口，也不寫 toString，
 * 避免不小心被印進 log。
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 只用 LINE 登入、沒提供 Email 的會員是 null */
    @Column(unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    /** 綁定的 LINE 使用者 id；沒綁定時是 null */
    @Column(name = "line_user_id", unique = true, length = 64)
    private String lineUserId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected Member() {
    }

    private Member(String email, String passwordHash, String googleSub, String displayName,
                   String avatarUrl, LocalDateTime createdAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.googleSub = googleSub;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.createdAt = createdAt;
    }

    /** Email + 密碼註冊（passwordHash 必須是已經雜湊過的值） */
    public static Member withPassword(String email, String passwordHash, String displayName, LocalDateTime now) {
        return new Member(email, passwordHash, null, displayName, null, now);
    }

    /** 第一次用 Google 登入，自動建立的會員（沒有密碼） */
    public static Member withGoogle(String email, String googleSub, String displayName, String avatarUrl,
                                    LocalDateTime now) {
        return new Member(email, null, googleSub, displayName, avatarUrl, now);
    }

    /**
     * 第一次用 LINE 登入，自動建立的會員（沒有密碼）。
     * LINE 的使用者 id 同時就是 LINE Bot 綁定用的 id（LINE Login 和 Bot 在同一個 provider 底下時兩邊一樣），
     * 所以用 LINE 登入的人不用再輸入綁定碼，就能收到每日回報。
     *
     * @param email LINE 沒提供時是 null
     */
    public static Member withLine(String lineUserId, String email, String displayName, String avatarUrl,
                                  LocalDateTime now) {
        Member member = new Member(email, null, null, displayName, avatarUrl, now);
        member.lineUserId = lineUserId;
        return member;
    }

    /** Email 一律去空白、轉小寫，「Abc@Gmail.com」和「abc@gmail.com」是同一個人 */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 已經用 Email 註冊的會員，之後用同一個 Email 的 Google 帳號登入，就綁在一起 */
    public void linkGoogle(String googleSub, String avatarUrl) {
        this.googleSub = googleSub;
        if (this.avatarUrl == null) {
            this.avatarUrl = avatarUrl;
        }
    }

    public void linkLine(String lineUserId) {
        this.lineUserId = lineUserId;
    }

    public void unlinkLine() {
        this.lineUserId = null;
    }

    public void recordLogin(LocalDateTime time) {
        this.lastLoginAt = time;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getGoogleSub() { return googleSub; }
    public String getLineUserId() { return lineUserId; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
