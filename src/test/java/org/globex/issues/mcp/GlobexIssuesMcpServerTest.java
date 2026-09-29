package org.globex.issues.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.globex.issues.mcp.model.IssueSummary;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@WithTestResource(StubGitLabResource.class)
class GlobexIssuesMcpServerTest {

    @Inject
    GlobexIssuesMcpServer server;

    @Inject
    ToolManager toolManager;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void allFourToolsAreRegisteredAndDescribed() {
        Set<String> names = StreamSupport.stream(toolManager.spliterator(), false)
                .map(ToolManager.ToolInfo::name)
                .collect(Collectors.toSet());
        assertTrue(names.containsAll(List.of("listIssues", "getIssue", "createIssue", "updateIssue")),
                "expected all four issue tools, got " + names);

        for (String name : List.of("listIssues", "getIssue", "createIssue", "updateIssue")) {
            ToolManager.ToolInfo tool = toolManager.getTool(name);
            assertNotNull(tool.description(), name + " has no description");
            assertFalse(tool.description().isBlank(), name + " has a blank description");
            for (ToolManager.ToolArgument arg : tool.arguments()) {
                assertFalse(arg.description() == null || arg.description().isBlank(),
                        name + "." + arg.name() + " has no description");
            }
        }
    }

    @Test
    void getIssueTrimsTheGitLabPayload() throws Exception {
        JsonNode issue = payloadOf(server.getIssue(7L));

        assertEquals(Set.of("iid", "title", "description", "state", "labels", "createdAt", "webUrl"),
                fieldNames(issue), "the tool must only return the trimmed issue shape");
        assertEquals(7, issue.get("iid").asInt());
        assertEquals("opened", issue.get("state").asText());
        assertEquals("billing", issue.get("labels").get(1).asText());
        assertEquals("2026-09-01T10:15:00.000Z", issue.get("createdAt").asText());

        assertEquals("/api/v4/projects/5/issues/7", StubGitLabResource.lastPath);
        assertEquals(StubGitLabResource.TEST_TOKEN, StubGitLabResource.lastPrivateToken,
                "every call must carry the PRIVATE-TOKEN header");
    }

    @Test
    void listIssuesPassesFiltersAndTrimsEveryIssue() throws Exception {
        JsonNode issues = payloadOf(server.listIssues("opened", "billing", 5));

        assertTrue(issues.isArray());
        assertEquals(1, issues.size());
        assertEquals(Set.of("iid", "title", "description", "state", "labels", "createdAt", "webUrl"),
                fieldNames(issues.get(0)));

        assertEquals("/api/v4/projects/5/issues", StubGitLabResource.lastPath);
        assertTrue(StubGitLabResource.lastQuery.contains("state=opened"), StubGitLabResource.lastQuery);
        assertTrue(StubGitLabResource.lastQuery.contains("labels=billing"), StubGitLabResource.lastQuery);
        assertTrue(StubGitLabResource.lastQuery.contains("per_page=5"), StubGitLabResource.lastQuery);
    }

    @Test
    void listIssuesOmitsEmptyFiltersAndCapsTheLimit() {
        assertFalse(server.listIssues(null, "  ", 5000).isError());

        assertFalse(StubGitLabResource.lastQuery.contains("state="), StubGitLabResource.lastQuery);
        assertFalse(StubGitLabResource.lastQuery.contains("labels="), StubGitLabResource.lastQuery);
        assertTrue(StubGitLabResource.lastQuery.contains("per_page=100"), StubGitLabResource.lastQuery);
    }

    @Test
    void createIssueSendsOnlyTheSuppliedFields() throws Exception {
        server.createIssue("Checkout fails", "Card on file is rejected.", null);

        assertEquals("POST", StubGitLabResource.lastMethod);
        assertEquals("/api/v4/projects/5/issues", StubGitLabResource.lastPath);

        JsonNode body = objectMapper.readTree(StubGitLabResource.lastBody);
        assertEquals(Set.of("title", "description"), fieldNames(body), "null labels must not be sent");
        assertEquals("Checkout fails", body.get("title").asText());
    }

    @Test
    void updateIssueTranslatesStateIntoAStateEvent() throws Exception {
        server.updateIssue(7L, null, null, "closed", "complaint,billing");

        assertEquals("PUT", StubGitLabResource.lastMethod);
        assertEquals("/api/v4/projects/5/issues/7", StubGitLabResource.lastPath);

        JsonNode body = objectMapper.readTree(StubGitLabResource.lastBody);
        assertEquals(Set.of("state_event", "labels"), fieldNames(body));
        assertEquals("close", body.get("state_event").asText());
    }

    @Test
    void updateIssueRejectsAnUnknownState() {
        ToolResponse response = server.updateIssue(7L, null, null, "banana", null);

        assertTrue(response.isError());
        assertTrue(((TextContent) response.firstContent()).text().contains("banana"));
    }

    @Test
    void issueSummaryDefaultsLabelsToAnEmptyList() {
        IssueSummary summary = IssueSummary.from(new org.globex.issues.mcp.model.GitLabIssueDto());
        assertEquals(List.of(), summary.labels());
    }

    private JsonNode payloadOf(ToolResponse response) throws Exception {
        assertFalse(response.isError(), () -> "tool call failed: " + response.firstContent());
        return objectMapper.readTree(((TextContent) response.firstContent()).text());
    }

    private static Set<String> fieldNames(JsonNode node) {
        return StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false)
                .collect(Collectors.toSet());
    }
}
