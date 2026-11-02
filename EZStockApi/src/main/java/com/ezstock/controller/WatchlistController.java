package com.ezstock.controller;

import com.ezstock.dto.WatchlistImportRequest;
import com.ezstock.dto.WatchlistItemResponse;
import com.ezstock.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自己的自選股（要登入）。會員 id 一律從 JWT 取，網址裡沒有會員 id，所以不可能改到別人的自選股。
 * 加入、移除都回傳最新的完整清單，前端直接用它更新畫面。
 */
@RestController
@RequestMapping("/api/members/me/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return watchlistService.list(memberId(jwt));
    }

    /** PUT：做幾次結果都一樣（冪等），重複加入同一檔不會出錯 */
    @PutMapping("/{stockId}")
    public List<WatchlistItemResponse> add(@AuthenticationPrincipal Jwt jwt, @PathVariable String stockId) {
        return watchlistService.add(memberId(jwt), stockId);
    }

    @DeleteMapping("/{stockId}")
    public List<WatchlistItemResponse> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String stockId) {
        return watchlistService.remove(memberId(jwt), stockId);
    }

    /** 第一次登入時，把瀏覽器裡的自選股匯入帳號 */
    @PostMapping("/import")
    public List<WatchlistItemResponse> importItems(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody WatchlistImportRequest request) {
        return watchlistService.importItems(memberId(jwt), request.stockIds());
    }

    private static long memberId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
