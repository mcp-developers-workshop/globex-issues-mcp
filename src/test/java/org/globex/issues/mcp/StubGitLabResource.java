package org.globex.issues.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * A throwaway GitLab stand-in on a random port. It lets the tests exercise the real REST
 * client - headers, paths, query parameters and JSON mapping - without a GitLab instance
 * and without a token.
 */
public class StubGitLabResource implements QuarkusTestResourceLifecycleManager {

    static final String TEST_TOKEN = "test-token-not-a-real-one";

    /** Details of the last request the stub received, for the tests to assert on. */
    static volatile String lastMethod;
    static volatile String lastPath;
    static volatile String lastQuery;
    static volatile String lastPrivateToken;
    static volatile String lastBody;

    /** A GitLab issue payload with far more fields than the tools are allowed to return. */
    private static final String ISSUE_JSON = """
            {
              "id": 901,
              "iid": 7,
              "project_id": 5,
              "title": "Checkout fails for saved cards",
              "description": "Card on file is rejected at the payment step.",
              "state": "opened",
              "labels": ["complaint", "billing"],
              "created_at": "2026-09-01T10:15:00.000Z",
              "updated_at": "2026-09-02T08:00:00.000Z",
              "web_url": "https://gitlab.example.com/globex/globex-issues/-/issues/7",
              "author": {"id": 3, "username": "asilva", "name": "Ana Silva"},
              "assignees": [],
              "milestone": null,
              "user_notes_count": 4,
              "upvotes": 1,
              "confidential": false,
              "time_stats": {"time_estimate": 0, "total_time_spent": 0}
            }
            """;

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the GitLab stub", e);
        }
        server.createContext("/api/v4/projects", this::handle);
        server.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.gitlab-api.url", "http://localhost:" + server.getAddress().getPort());
        config.put("gitlab.token", TEST_TOKEN);
        return config;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastMethod = exchange.getRequestMethod();
        lastPath = exchange.getRequestURI().getPath();
        lastQuery = exchange.getRequestURI().getQuery();
        lastPrivateToken = exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN");
        try (InputStream in = exchange.getRequestBody()) {
            lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // A list request gets an array, everything else gets a single issue
        boolean list = "GET".equals(lastMethod) && lastPath.endsWith("/issues");
        byte[] body = (list ? "[" + ISSUE_JSON + "]" : ISSUE_JSON).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
