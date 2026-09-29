# globex-issues-mcp

A Quarkus MCP server that exposes the GitLab Issues API of the `globex/globex-issues`
project as MCP tools, so an agent can look at the Globex backlog and raise or update
issues on it.

It is built with the `quarkus-mcp-server-http` extension and speaks **streamable HTTP**
on `http://localhost:8080/mcp`. It is the sibling of `globex-store-mcp` and follows the
same layout: a single `@Singleton` holding the tools, a `@RegisterRestClient` interface
per upstream API, and DTO/summary types under `model`.

## Tools

| Tool | GitLab call | Arguments |
| --- | --- | --- |
| `listIssues` | `GET /api/v4/projects/{id}/issues` | `state` (optional), `labels` (optional), `limit` (optional, default 20, max 100) |
| `getIssue` | `GET /api/v4/projects/{id}/issues/{iid}` | `iid` |
| `createIssue` | `POST /api/v4/projects/{id}/issues` | `title`, `description`, `labels` (optional) |
| `updateIssue` | `PUT /api/v4/projects/{id}/issues/{iid}` | `iid`, `title` (optional), `description` (optional), `state` (optional, `close` or `reopen`), `labels` (optional) |

### What the tools return

Not GitLab's issue JSON. A GitLab issue carries around forty fields - author, assignees,
milestone, time stats, references, task completion status - and none of that helps a model
answer a question about the backlog. Every tool returns the same trimmed record instead:

```json
{
  "iid": 7,
  "title": "Checkout fails for saved cards",
  "description": "Card on file is rejected at the payment step.",
  "state": "opened",
  "labels": ["complaint", "billing"],
  "createdAt": "2026-09-01T10:15:00.000Z",
  "webUrl": "https://gitlab.example.com/globex/globex-issues/-/issues/7"
}
```

This is the same data minimisation lesson as Module 1: shape the payload for the model,
not for the API.

Two other things worth knowing about the tool layer:

- `updateIssue` takes a `state` of `close` or `reopen` and translates it into GitLab's
  `state_event` field. `closed` and `open` are accepted too, because that is what models
  tend to say. Anything else comes back as a tool error rather than a GitLab 400.
- Only the arguments you supply are sent. Updating just the title leaves the description
  and labels alone.

## Configuration

| Property | Lives in | Notes |
| --- | --- | --- |
| `quarkus.mcp.server.server-info.name` | `src/main/resources/application.properties` | Build time. Server identity reported to the client. |
| `quarkus.mcp.server.server-info.version` | `src/main/resources/application.properties` | Build time. |
| `quarkus.mcp.server.http.root-path` | `src/main/resources/application.properties` | Build time. Streamable HTTP is served at `/mcp`. |
| `quarkus.rest-client.gitlab-api.url` | `src/main/resources/application.properties` (default) | Run time. Override in `.env` or with `QUARKUS_REST_CLIENT_GITLAB_API_URL`. |
| `gitlab.project-id` | `src/main/resources/application.properties` (default `5`) | Run time. Not a secret, so the workshop default is committed. Override with `GITLAB_PROJECT_ID`. |
| `gitlab.token` | `.env` | Run time. `application.properties` only carries an obvious placeholder. Override with `GITLAB_TOKEN`. |

The split matters. In Quarkus some configuration is `BUILD_AND_RUN_TIME_FIXED` - transport
selection is the classic example, and the Quarkus LangChain4j
`quarkus.langchain4j.mcp.<name>.transport-type` property caught this project out once
already: put it in `.env` and it is silently ignored, because the value was baked in when
the application was built. Anything fixed at build time therefore lives in
`application.properties`, which is committed. `.env` carries only genuinely run time
values: the token and, if you need it, the base URL and project id.

### Pointing it at a GitLab project access token

1. In GitLab, open `globex/globex-issues` → **Settings → Access tokens**, create a project
   access token with the `api` scope and the Developer role, and copy it. GitLab shows it
   once.
2. Copy `.env.example` to `.env` and paste the token in:

   ```shell script
   cp .env.example .env
   ```

   ```properties
   GITLAB_TOKEN=glpat-your-real-token
   ```

`.env` is in `.gitignore`. Do not commit it.

The token is sent as a `PRIVATE-TOKEN` header on every call, declared once on the REST
client interface:

```java
@ClientHeaderParam(name = "PRIVATE-TOKEN", value = "${gitlab.token}")
```

At runtime, for example on OpenShift, set the `GITLAB_TOKEN` environment variable from a
secret instead of shipping a `.env` file.

## Running the application in dev mode

```shell script
./mvnw quarkus:dev
```

Dev mode picks up `.env` automatically. The Dev UI is at <http://localhost:8080/q/dev/>,
and the MCP endpoint is at <http://localhost:8080/mcp>.

To check the tools are registered, without a token:

```shell script
curl -s -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

then repeat with `"method":"tools/list"`, passing back the `Mcp-Session-Id` header from
the first response.

## Tests

```shell script
./mvnw test
```

The tests run the real REST client against a throwaway HTTP stub on a random port
(`StubGitLabResource`), so they cover the client wiring, the `PRIVATE-TOKEN` header, the
paths and query parameters, and the fact that the responses are trimmed to the seven
fields above. No GitLab instance and no token are needed.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it's not an _über-jar_ as the dependencies are copied into the
`target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a
container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/globex-issues-mcp-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Decisions worth knowing

- **Package and group id.** `org.globex.issues.mcp`, following `globex-store-mcp`'s
  `org.globex.retail.mcp`. The issue tracker is not part of the retail application, so it
  did not seem right to file it under `retail`.
- **Base URL is committed.** `globex-store-mcp` commits its upstream URL in
  `application.properties`, so this project does the same for consistency. It is still a
  run time property and `.env` overrides it.
- **Labels are comma separated strings**, not arrays. GitLab accepts both; a string is
  easier for a model to produce and easier to describe in a tool argument.
- **`updateIssue` replaces labels rather than appending**, because that is what GitLab's
  `labels` field does. The tool description says so explicitly.
- **The JSON shape was written from the GitLab API documentation, not verified against the
  workshop cluster.** Unauthenticated reads of
  `globex/globex-issues` on the TechZone cluster return `404 Project Not Found`, so the
  project is private and no anonymous request could confirm the payload. `GitLabIssueDto`
  ignores unknown fields, so extra fields are harmless, but the field names
  (`created_at`, `web_url`, `iid`) should be sanity checked against a real response once a
  token is available.
