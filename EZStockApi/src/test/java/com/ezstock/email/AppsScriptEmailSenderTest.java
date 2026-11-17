package com.ezstock.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 不連真的 Google，用 MockRestServiceServer 模擬 Apps Script 的回應 */
class AppsScriptEmailSenderTest {

    private static final String URL = "https://script.test/macros/s/abc/exec";

    private MockRestServiceServer server;
    private AppsScriptEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        EmailProperties properties = new EmailProperties(null, null, null, null,
                new EmailProperties.AppsScript(URL, "the-secret"));
        sender = new AppsScriptEmailSender(properties, builder);
    }

    @Test
    @DisplayName("寄信：POST 帶密語、收件人、主旨、內容；回 ok=true 就成功")
    void send() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.secret").value("the-secret"))
                .andExpect(jsonPath("$.to").value("a@example.com"))
                .andExpect(jsonPath("$.subject").value("主旨"))
                .andExpect(jsonPath("$.text").value("內容"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.send("a@example.com", "主旨", "內容")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("寄信：密語不對，Apps Script 回 ok=false，要丟例外")
    void unauthorized() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"unauthorized\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send("a@example.com", "主旨", "內容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unauthorized");
    }

    @Test
    @DisplayName("寄信：Apps Script 回錯誤狀態碼也要丟例外")
    void httpError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> sender.send("a@example.com", "主旨", "內容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }
}
