package org.globex.issues.mcp.model;

import java.util.List;

/**
 * The trimmed issue shape handed back to the model. Only the fields an agent needs to
 * reason about an issue are included - internal ids, author, milestone, time stats and
 * the rest of the GitLab payload are dropped before they ever reach the context window.
 */
public record IssueSummary(
        Long iid,
        String title,
        String description,
        String state,
        List<String> labels,
        String createdAt,
        String webUrl) {

    public static IssueSummary from(GitLabIssueDto issue) {
        return new IssueSummary(
                issue.iid,
                issue.title,
                issue.description,
                issue.state,
                issue.labels != null ? issue.labels : List.of(),
                issue.createdAt,
                issue.webUrl);
    }
}
