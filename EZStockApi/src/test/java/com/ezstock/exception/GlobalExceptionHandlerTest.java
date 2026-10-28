package com.ezstock.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 直接呼叫 GlobalExceptionHandler 的方法，確認例外被轉成正確的狀態碼。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("網址不存在：回 404，不是 500")
    void noResourceIsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stock/2330");

        ResponseEntity<ApiError> response =
                handler.handleNoResource(mock(NoResourceFoundException.class), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("找不到 /api/stock/2330");
    }
}
