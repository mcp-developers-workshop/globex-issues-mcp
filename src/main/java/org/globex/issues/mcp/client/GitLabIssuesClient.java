package org.globex.issues.mcp.client;

import java.util.List;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.globex.issues.mcp.model.CreateIssueRequest;
import org.globex.issues.mcp.model.GitLabIssueDto;
import org.globex.issues.mcp.model.UpdateIssueRequest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * GitLab Issues API. Every call is authenticated with a project access token sent in the
 * PRIVATE-TOKEN header, resolved from the 'gitlab.token' configuration property.
 */
@RegisterRestClient(configKey = "gitlab-api")
@ClientHeaderParam(name = "PRIVATE-TOKEN", value = "${gitlab.token}")
@Path("/api/v4/projects")
public interface GitLabIssuesClient {

    @GET
    @Path("/{projectId}/issues")
    @Produces(MediaType.APPLICATION_JSON)
    List<GitLabIssueDto> listIssues(@PathParam("projectId") String projectId,
            @QueryParam("state") String state,
            @QueryParam("labels") String labels,
            @QueryParam("per_page") Integer perPage);

    @GET
    @Path("/{projectId}/issues/{iid}")
    @Produces(MediaType.APPLICATION_JSON)
    GitLabIssueDto getIssue(@PathParam("projectId") String projectId, @PathParam("iid") Long iid);

    @POST
    @Path("/{projectId}/issues")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GitLabIssueDto createIssue(@PathParam("projectId") String projectId, CreateIssueRequest request);

    @PUT
    @Path("/{projectId}/issues/{iid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GitLabIssueDto updateIssue(@PathParam("projectId") String projectId, @PathParam("iid") Long iid,
            UpdateIssueRequest request);

}
