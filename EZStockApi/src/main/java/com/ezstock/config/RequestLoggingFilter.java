package com.ezstock.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 每個 HTTP 請求進來時：
 * 1. 產生一個 traceId，放進 MDC，這個請求之後印的每一行 log 都會帶著它
 * 2. 記錄請求的方法、路徑、狀態碼、耗時
 * 3. 把 traceId 放進回應標頭 X-Trace-Id，前端回報問題時可以拿它來查 log
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        String query = request.getQueryString();
        String target = request.getMethod() + " " + request.getRequestURI() + (query == null ? "" : "?" + query);
        long start = System.currentTimeMillis();
        log.info("--> {}", target);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("<-- {} {} {} ms", response.getStatus(), target, System.currentTimeMillis() - start);
            MDC.remove(TRACE_ID);
        }
    }
}
