package com.ezstock.controller;

import com.ezstock.dto.AssetSummaryResponse;
import com.ezstock.dto.HoldingRequest;
import com.ezstock.dto.MoneyAccountRequest;
import com.ezstock.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自己的資產儀表板（要登入）。會員 id 一律從 JWT 取。
 * 每個修改都回傳最新的儀表板，前端不用再多打一次 GET。
 */
@RestController
@RequestMapping("/api/members/me/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public AssetSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return assetService.summary(memberId(jwt));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetSummaryResponse createAccount(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody MoneyAccountRequest request) {
        return assetService.createAccount(memberId(jwt), request);
    }

    @PutMapping("/accounts/{accountId}")
    public AssetSummaryResponse updateAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable long accountId,
                                              @Valid @RequestBody MoneyAccountRequest request) {
        return assetService.updateAccount(memberId(jwt), accountId, request);
    }

    @DeleteMapping("/accounts/{accountId}")
    public AssetSummaryResponse deleteAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable long accountId) {
        return assetService.deleteAccount(memberId(jwt), accountId);
    }

    /** PUT：同一檔再送一次就是修改（冪等） */
    @PutMapping("/holdings/{stockId}")
    public AssetSummaryResponse saveHolding(@AuthenticationPrincipal Jwt jwt, @PathVariable String stockId,
                                            @Valid @RequestBody HoldingRequest request) {
        return assetService.saveHolding(memberId(jwt), stockId, request);
    }

    @DeleteMapping("/holdings/{stockId}")
    public AssetSummaryResponse deleteHolding(@AuthenticationPrincipal Jwt jwt, @PathVariable String stockId) {
        return assetService.deleteHolding(memberId(jwt), stockId);
    }

    private static long memberId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
