package com.ezstock.security;

import com.ezstock.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 不連真的 LINE，用 MockRestServiceServer 模擬回應 */
class LineLoginClientTest {

    private static final String BASE = "https://line.test";

    private MockRestServiceServer server;
    private LineLoginClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LineLoginClient(
                new LineLoginProperties("1234567890", "line-secret", "http://localhost:5173/login/line/callback"), builder);
    }

    @Test
    @DisplayName("授權網址：帶 client_id、redirect_uri、state、nonce、scope，並邀請加 Bot 好友")
    void authorizeUrl() {
        var params = UriComponentsBuilder.fromUriString(client.authorizeUrl("state-abc", "nonce-xyz")).build().getQueryParams();

        assertThat(client.authorizeUrl("s", "n")).startsWith(LineLoginClient.AUTHORIZE_URL);
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("1234567890");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("http://localhost:5173/login/line/callback");
        assertThat(params.getFirst("state")).isEqualTo("state-abc");
        assertThat(params.getFirst("nonce")).isEqualTo("nonce-xyz");
        assertThat(params.getFirst("scope")).isEqualTo("profile%20openid%20email");
        assertThat(params.getFirst("bot_prompt")).isEqualTo("aggressive");
    }

    @Test
    @DisplayName("登入：用 code 換 ID Token（帶 channel secret），再帶 nonce 驗證，拿到使用者資料")
    void authenticate() {
        server.expect(requestTo(BASE + "/oauth2/v2.1/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "authorization_code", "code", "the-code", "client_secret", "line-secret")))
                .andRespond(withSuccess("{\"access_token\":\"at\",\"id_token\":\"the-id-token\",\"expires_in\":2592000}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/oauth2/v2.1/verify"))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "id_token", "the-id-token", "client_id", "1234567890", "nonce", "the-nonce")))
                .andRespond(withSuccess("{\"iss\":\"https://access.line.me\",\"sub\":\"U123\",\"name\":\"小明\","
                        + "\"picture\":\"https://profile.line-scdn.net/a\",\"email\":\"a@example.com\"}", MediaType.APPLICATION_JSON));

        LineLoginUser user = client.authenticate("the-code", "the-nonce");

        server.verify();
        assertThat(user).isEqualTo(new LineLoginUser("U123", "小明", "https://profile.line-scdn.net/a", "a@example.com"));
    }

    @Test
    @DisplayName("code 過期或 nonce 不對（LINE 回 400）：回 401，不把細節丟給使用者")
    void lineRejects() {
        server.expect(requestTo(BASE + "/oauth2/v2.1/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"invalid authorization code\"}"));

        assertThatThrownBy(() -> client.authenticate("expired", "n"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("LINE 登入失敗，請再試一次");
    }
}
