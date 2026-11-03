package com.ezstock.news;

import com.ezstock.config.RequestLoggingFilter;
import com.ezstock.dto.NewsResponse;
import com.ezstock.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsControllerTest {

    private NewsService newsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsService = mock(NewsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("用逗號分隔多檔股票，limit 預設 20")
    void latest() throws Exception {
        when(newsService.latest(List.of("2330", "2317"), 20)).thenReturn(List.of(
                new NewsResponse("電子股走強", "測試新聞", "https://news.test/a", LocalDateTime.of(2026, 11, 3, 13, 30),
                        List.of(new NewsResponse.RelatedStock("2330", "台積電")))));

        mockMvc.perform(get("/api/news").param("stockIds", "2330,2317"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("電子股走強"))
                .andExpect(jsonPath("$[0].publishedAt").value("2026-11-03T13:30:00"))
                .andExpect(jsonPath("$[0].stocks[0].name").value("台積電"));
    }

    @Test
    @DisplayName("沒帶 stockIds 回 400")
    void missingStockIds() throws Exception {
        mockMvc.perform(get("/api/news")).andExpect(status().isBadRequest());
    }
}
