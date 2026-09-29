package org.globex.issues.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of PUT /api/v4/projects/{id}/issues/{iid}. Null members are dropped, so an update
 * that only carries a title leaves the description and labels untouched.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateIssueRequest(
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("state_event") String stateEvent,
        @JsonProperty("labels") String labels) {
}
