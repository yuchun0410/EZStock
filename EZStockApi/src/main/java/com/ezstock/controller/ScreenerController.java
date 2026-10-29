package com.ezstock.controller;

import com.ezstock.dto.ScreenRequest;
import com.ezstock.dto.ScreenResponse;
import com.ezstock.service.ScreenerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screener")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    /** 條件選股，條件的格式跟條件回測一樣，例如 [{"type": "CLOSE_ABOVE_MOVING_AVERAGE", "value": 20}] */
    @PostMapping
    public ScreenResponse screen(@Valid @RequestBody ScreenRequest request) {
        return screenerService.screen(request);
    }
}
