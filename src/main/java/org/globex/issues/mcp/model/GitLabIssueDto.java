package org.globex.issues.mcp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The subset of the GitLab issue payload we care about. GitLab returns roughly forty
 * fields per issue, everything else is ignored on the way in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabIssueDto {

    public Long id;
    public Long iid;
    public String title;
    public String description;
    public String state;
    public List<String> labels;

    @JsonProperty("created_at")
    public String createdAt;

    @JsonProperty("web_url")
    public String webUrl;
}
