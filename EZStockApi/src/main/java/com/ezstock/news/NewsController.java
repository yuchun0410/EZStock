package com.ezstock.news;

import com.ezstock.dto.NewsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 個股相關新聞（不需要登入）。
 * 例如 GET /api/news?stockIds=2330,2317&limit=20
 * 首頁帶熱門股 + 自選股的代號，個股頁只帶一檔。
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsResponse> latest(@RequestParam List<String> stockIds,
                                     @RequestParam(defaultValue = "20") int limit) {
        return newsService.latest(stockIds, limit);
    }
}
