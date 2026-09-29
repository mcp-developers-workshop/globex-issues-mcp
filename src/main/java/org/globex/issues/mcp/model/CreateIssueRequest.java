package org.globex.issues.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /api/v4/projects/{id}/issues. Null members are dropped so GitLab only
 * sees the fields the caller actually supplied.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateIssueRequest(
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("labels") String labels) {
}
