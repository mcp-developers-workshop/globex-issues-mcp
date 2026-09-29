package org.globex.issues.mcp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.globex.issues.mcp.client.GitLabIssuesClient;
import org.globex.issues.mcp.model.CreateIssueRequest;
import org.globex.issues.mcp.model.GitLabIssueDto;
import org.globex.issues.mcp.model.IssueSummary;
import org.globex.issues.mcp.model.UpdateIssueRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class GlobexIssuesMcpServer {

    /** GitLab caps per_page at 100. */
    static final int MAX_PER_PAGE = 100;

    static final int DEFAULT_PER_PAGE = 20;

    @RestClient
    GitLabIssuesClient issuesClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "gitlab.project-id")
    String projectId;

    @Tool(name = "listIssues",
          description = "Browse the issues raised against the Globex issue tracker. "
                  + "Use this to find out what is already being worked on, to check whether a problem "
                  + "has been reported before, or to summarise the current backlog. "
                  + "Returns the issue number, title, description, state, labels, creation date and web link for each issue.")
    public ToolResponse listIssues(
            @ToolArg(name = "state", required = false,
                     description = "Narrow the results to issues that are still open or to issues that have been closed. "
                             + "Use 'opened' or 'closed'. Leave this out to see both.") String state,
            @ToolArg(name = "labels", required = false,
                     description = "Only return issues carrying all of these labels, as a comma separated list, "
                             + "for example 'billing,urgent'. Leave this out to see issues with any labels.") String labels,
            @ToolArg(name = "limit", required = false,
                     description = "How many issues to bring back, newest first. Defaults to 20, the maximum is 100.") Integer limit) {

        try {
            int perPage = limit != null ? Math.clamp(limit, 1, MAX_PER_PAGE) : DEFAULT_PER_PAGE;
            List<GitLabIssueDto> issues = issuesClient.listIssues(projectId, blankToNull(state), blankToNull(labels),
                    perPage);

            List<IssueSummary> summaries = new ArrayList<>();
            for (GitLabIssueDto issue : issues) {
                summaries.add(IssueSummary.from(issue));
            }
            return json(summaries);
        } catch (Exception e) {
            return ToolResponse.error("Failed to list issues: " + e.getMessage());
        }
    }

    @Tool(name = "getIssue",
          description = "Read one specific issue in full, when you already know its issue number. "
                  + "Use this to pick up the detail behind an issue you found with listIssues, "
                  + "or to confirm the current state of an issue before changing it.")
    public ToolResponse getIssue(
            @ToolArg(name = "iid",
                     description = "The issue number as it is shown to people, for example the 7 in '#7'. "
                             + "This is not GitLab's internal database id.") Long iid) {

        try {
            GitLabIssueDto issue = issuesClient.getIssue(projectId, iid);
            return json(IssueSummary.from(issue));
        } catch (Exception e) {
            return ToolResponse.error("Failed to retrieve issue #" + iid + ": " + e.getMessage());
        }
    }

    @Tool(name = "createIssue",
          description = "Raise a new issue in the Globex issue tracker. "
                  + "Use this when something needs to be tracked and fixed by a human, such as a customer "
                  + "complaint that cannot be resolved in the conversation. "
                  + "Check with listIssues first so you do not raise a duplicate. "
                  + "Returns the newly created issue, including the number it was given.")
    public ToolResponse createIssue(
            @ToolArg(name = "title",
                     description = "A short one line summary of the problem, as someone triaging the "
                             + "backlog would want to read it.") String title,
            @ToolArg(name = "description",
                     description = "The full story: what happened, who it affects and anything already tried. "
                             + "Markdown is supported.") String description,
            @ToolArg(name = "labels", required = false,
                     description = "Labels to file the issue under, as a comma separated list, "
                             + "for example 'complaint,billing'. Leave this out if you are unsure.") String labels) {

        try {
            CreateIssueRequest request = new CreateIssueRequest(title, description, blankToNull(labels));
            GitLabIssueDto issue = issuesClient.createIssue(projectId, request);
            return json(IssueSummary.from(issue));
        } catch (Exception e) {
            return ToolResponse.error("Failed to create issue '" + title + "': " + e.getMessage());
        }
    }

    @Tool(name = "updateIssue",
          description = "Change an issue that already exists: correct its title or description, "
                  + "add labels, or close it once the problem is resolved and reopen it if it comes back. "
                  + "Only the things you supply are changed, everything else is left as it is. "
                  + "Returns the issue as it stands after the change.")
    public ToolResponse updateIssue(
            @ToolArg(name = "iid",
                     description = "The issue number of the issue to change, for example the 7 in '#7'.") Long iid,
            @ToolArg(name = "title", required = false,
                     description = "A replacement one line summary. Leave this out to keep the existing title.") String title,
            @ToolArg(name = "description", required = false,
                     description = "A replacement description. This overwrites the old one, so include "
                             + "anything from it that should survive. Leave this out to keep the existing description.") String description,
            @ToolArg(name = "state", required = false,
                     description = "Use 'close' when the problem is resolved, or 'reopen' when a closed issue "
                             + "turns out to still be a problem. Leave this out to leave the issue as it is.") String state,
            @ToolArg(name = "labels", required = false,
                     description = "A replacement comma separated list of labels. This replaces the labels "
                             + "already on the issue rather than adding to them.") String labels) {

        try {
            String stateEvent = toStateEvent(state);
            UpdateIssueRequest request = new UpdateIssueRequest(blankToNull(title), blankToNull(description),
                    stateEvent, blankToNull(labels));
            GitLabIssueDto issue = issuesClient.updateIssue(projectId, iid, request);
            return json(IssueSummary.from(issue));
        } catch (IllegalArgumentException e) {
            return ToolResponse.error(e.getMessage());
        } catch (Exception e) {
            return ToolResponse.error("Failed to update issue #" + iid + ": " + e.getMessage());
        }
    }

    /**
     * GitLab expects a state_event rather than a state on an update. Models tend to say
     * 'closed' when they mean 'close', so accept both spellings.
     */
    static String toStateEvent(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return switch (state.trim().toLowerCase()) {
            case "close", "closed" -> "close";
            case "reopen", "reopened", "open", "opened" -> "reopen";
            default -> throw new IllegalArgumentException(
                    "Unsupported state '" + state + "', expected 'close' or 'reopen'");
        };
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ToolResponse json(Object payload) {
        try {
            return ToolResponse.success(new TextContent(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            return ToolResponse.error("Failed to serialize response: " + e.getMessage());
        }
    }
}
