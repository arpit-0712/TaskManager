package org.parkar.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{\"choices\":[{\"message\":{\"content\":\"hello there\"}}]}";
    private volatile String modelsResponse = "{\"data\":[{\"id\":\"gpt-4o-mini\"},{\"id\":\"gpt-4o\"}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/models", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = modelsResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleClient client() {
        return new OpenAiCompatibleClient(baseUrl, "test-model", "test-key",
                Duration.ofSeconds(5), 0.2, 128);
    }

    @Test
    void parsesContentAndSendsBearer() {
        String reply = client().complete("system", "user");
        assertEquals("hello there", reply);
        assertEquals("Bearer test-key", lastAuth.get());
    }

    @Test
    void missingApiKeyFails() {
        OpenAiCompatibleClient noKey = new OpenAiCompatibleClient(baseUrl, "m", "",
                Duration.ofSeconds(5), 0.2, 128);
        assertThrows(LlmException.class, () -> noKey.complete("s", "u"));
    }

    @Test
    void httpErrorRaisesLlmException() {
        status = 401;
        response = "{\"error\":{\"message\":\"bad key\"}}";
        LlmException ex = assertThrows(LlmException.class, () -> client().complete("s", "u"));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void listsAvailableModels() {
        var models = client().availableModels();
        assertEquals(List.of("gpt-4o-mini", "gpt-4o"), models);
        assertEquals("Bearer test-key", lastAuth.get());
    }
}
