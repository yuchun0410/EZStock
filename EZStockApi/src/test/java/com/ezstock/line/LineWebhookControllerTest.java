package com.ezstock.line;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineWebhookControllerTest {

    private static final String SECRET = "test-channel-secret";
    private static final String MESSAGE = """
            {"destination":"Uxxx","events":[{"type":"message","replyToken":"reply-1",
             "source":{"type":"user","userId":"U1234567890"},
             "message":{"id":"1","type":"text","text":"2330"}}]}""";

    private LineCommandHandler handler;
    private LineMessagingClient client;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        handler = mock(LineCommandHandler.class);
        client = mock(LineMessagingClient.class);
        LineSignatureVerifier verifier = new LineSignatureVerifier(new LineProperties(SECRET, "token", null));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LineWebhookController(verifier, handler, client, JsonMapper.builder().build())).build();
        when(handler.handle(anyString(), anyString())).thenReturn(List.of("2330 台積電"));
    }

    @Test
    @DisplayName("簽章正確的文字訊息：交給指令處理，用 replyToken 回覆")
    void message() throws Exception {
        mockMvc.perform(post("/api/line/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", LineSignatureVerifierTest.sign(bytes(MESSAGE), SECRET))
                        .content(MESSAGE))
                .andExpect(status().isOk());

        verify(handler).handle("U1234567890", "2330");
        verify(client).reply("reply-1", List.of("2330 台積電"));
    }

    @Test
    @DisplayName("簽章不對：401，什麼都不做")
    void forged() throws Exception {
        mockMvc.perform(post("/api/line/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", LineSignatureVerifierTest.sign(bytes(MESSAGE), "wrong"))
                        .content(MESSAGE))
                .andExpect(status().isUnauthorized());

        verify(handler, never()).handle(anyString(), anyString());
        verify(client, never()).reply(any(), any());
    }

    @Test
    @DisplayName("後台按 Verify（沒有事件）：200")
    void verify200() throws Exception {
        String body = "{\"destination\":\"Uxxx\",\"events\":[]}";
        mockMvc.perform(post("/api/line/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Line-Signature", LineSignatureVerifierTest.sign(bytes(body), SECRET))
                        .content(body))
                .andExpect(status().isOk());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
