package com.ezstock.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 不連真的 Mailjet，用 MockRestServiceServer 模擬回應 */
class MailjetEmailSenderTest {

    private static final String BASE = "https://mailjet.test";

    private MockRestServiceServer server;
    private MailjetEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        EmailProperties properties = new EmailProperties("http://localhost:5173/verify-email", "noreply@example.com",
                "EZStock", new EmailProperties.Mailjet("public-key", "secret-key"), null);
        sender = new MailjetEmailSender(properties, builder);
    }

    @Test
    @DisplayName("寄信：Basic Auth 帶 API Key 和 Secret Key，內容是 Messages 陣列；回 success 就成功")
    void send() {
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString("public-key:secret-key".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE + "/v3.1/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basic))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.Messages[0].From.Email").value("noreply@example.com"))
                .andExpect(jsonPath("$.Messages[0].From.Name").value("EZStock"))
                .andExpect(jsonPath("$.Messages[0].To[0].Email").value("a@example.com"))
                .andExpect(jsonPath("$.Messages[0].Subject").value("主旨"))
                .andExpect(jsonPath("$.Messages[0].TextPart").value("內容"))
                .andRespond(withSuccess("{\"Messages\":[{\"Status\":\"success\",\"To\":[{\"Email\":\"a@example.com\","
                        + "\"MessageID\":456}]}]}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.send("a@example.com", "主旨", "內容")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("寄信：金鑰錯（401）丟例外，讓呼叫的人記 log")
    void unauthorized() {
        server.expect(requestTo(BASE + "/v3.1/send"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"ErrorMessage\":\"API key authentication/authorization failure\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send("a@example.com", "主旨", "內容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("寄信：Mailjet 回的 Status 不是 success（例如寄件人沒驗證）也算失敗")
    void notSuccess() {
        server.expect(requestTo(BASE + "/v3.1/send"))
                .andRespond(withSuccess("{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":\"x\"}]}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send("a@example.com", "主旨", "內容"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("error");
    }
}
