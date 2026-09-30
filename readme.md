# Engineering Data Service

![Java](https://img.shields.io/badge/Java-21+-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Supported Files](#-supported-files)
- [Architecture](#-architecture)
- [Core Components](#-core-components)
- [Prerequisites](#-prerequisites)
- [Configuration](#-configuration)
- [Security](#-security)
- [Database](#-database)
- [Running Locally](#-running-locally)
- [REST API](#-rest-api)
- [Processing Behavior](#-processing-behavior)
- [Operations and Troubleshooting](#-operations-and-troubleshooting)
- [License](#-license)

---

## 📌 Overview

**Engineering Data Service** is a Spring Boot service that ingests source code, build manifests, documentation, diagrams, configuration files, and ZIP archives. It converts accepted content into Azure OpenAI embeddings and stores both canonical source files and chunk-level vectors in PostgreSQL with pgvector.

The service is intended to populate an engineering knowledge corpus for downstream retrieval-augmented generation (RAG) and semantic-search consumers.

The ingestion flow is asynchronous:

1. An authenticated client uploads one or more files.
2. The service validates and temporarily stores each upload.
3. ZIP metadata is inferred from Maven or Gradle files when available.
4. Text is tokenized into overlapping chunks.
5. Azure OpenAI generates an embedding for each metadata-enriched chunk.
6. Canonical content and chunk embeddings are persisted in the `engineering_reference` schema.
7. Temporary local files are removed after processing.

> A `202 Accepted` response confirms that an upload was queued. It does not guarantee that asynchronous embedding generation and persistence have completed.

---

## 🚀 Features

- Multi-file multipart upload API
- Individual file and ZIP archive ingestion
- Broad source-code, manifest, documentation, diagram, and configuration support
- Maven and Gradle module/version inference for ZIP archives
- Token-aware chunking with configurable overlap and per-file limits
- Azure OpenAI embedding generation
- PostgreSQL and pgvector persistence
- Agent-friendly semantic-search REST API using cosine similarity
- Optional search filters for module, module version, and deprecated content
- Canonical-file upsert by module, version, and path
- Asynchronous processing with a configurable thread pool
- MDC request-context propagation into asynchronous tasks
- Module-level soft deprecation, restoration, re-embedding, and hard deletion
- Stateless Keycloak/OIDC JWT authentication
- Realm-role and client-role mapping to Spring Security authorities
- OpenAPI 3 and Swagger UI with Keycloak authorization-code configuration
- ZIP-slip protection and ZIP bomb guardrails
- Structured operational logging
- JUnit 5, Spring Boot Test, H2, and Mockito test coverage

---

## 📄 Supported Files

File matching is case-insensitive and is based on either an exact file name or a suffix.

| Category | Supported names and extensions |
|---|---|
| Maven | `pom.xml`, `*.pom`, `mvnw`, `mvnw.cmd` |
| Gradle | `build.gradle`, `settings.gradle`, `*.gradle`, `build.gradle.kts`, `settings.gradle.kts`, `*.gradle.kts`, `gradlew`, `gradlew.bat` |
| Other manifests | `composer.json`, `composer.lock`, `Cargo.toml`, `Cargo.lock`, `pyproject.toml`, `requirements.txt`, `Pipfile`, `Pipfile.lock`, `setup.py`, `setup.cfg` |
| Native/.NET builds | `CMakeLists.txt`, `*.cmake`, `Makefile`, `GNUmakefile`, `*.sln`, `*.csproj`, `*.fsproj`, `*.vbproj`, `packages.config`, `*.nuspec` |
| Languages | `*.java`, `*.php`, `*.phtml`, `*.php3`–`*.php8`, `*.phpt`, `*.phps`, `*.py`, `*.pyi`, `*.c`, `*.h`, `*.cpp`, `*.cxx`, `*.cc`, `*.hpp`, `*.hxx`, `*.hh`, `*.cs`, `*.csx`, `*.rs` |
| Web | `*.html`, `*.htm`, `*.js`, `*.mjs`, `*.cjs`, `*.css` |
| Documentation and diagrams | `*.md`, `*.markdown`, `*.mmd`, `*.mermaid`, `*.puml`, `*.plantuml`, `*.drawio`, `*.dio` |
| Data and configuration | `*.sql`, `*.yml`, `*.yaml`, `*.toml`, `*.xml`, `*.json`, `*.properties`, `*.csv`, `*.txt` |
| Containers | `Dockerfile`, `docker-compose.yml`, `docker-compose.yaml`, `*.dockerfile` |
| Archives | `*.zip` containing supported files |

Unsupported standalone files are rejected. Unsupported entries inside a ZIP are skipped.

### ZIP guardrails

| Limit | Current value |
|---|---:|
| Maximum uncompressed size per extracted entry | 512 KiB |
| Maximum total uncompressed extracted data | 10 MiB |
| Maximum archive entries considered | 10,000 |

ZIP extraction normalizes paths and rejects entries that would escape the temporary extraction directory.

---

## 🏗 Architecture

```text readme.md
Authenticated API Client
        │
        │ Bearer JWT + multipart/form-data
        ▼
Spring Boot REST API
        │
        ├─ FileUploadController
        │    ├─ validates files and metadata
        │    ├─ stores uploads temporarily
        │    └─ inspects/extracts ZIP archives
        │
        ├─ ProcessingService (@Async)
        │    ├─ reads UTF-8 content
        │    ├─ upserts canonical source metadata/content
        │    ├─ tokenizes content into overlapping chunks
        │    └─ enriches each chunk with metadata
        │
        ├─ EmbeddingService
        │    └─ Azure OpenAI Embeddings API
        │
        ├─ EmbeddingSearchController
        │    └─ embeds queries and retrieves nearest chunks
        │
        ├─ JPA repositories
        │    └─ PostgreSQL + pgvector
        │         ├─ engineering_reference.canonical_files
        │         └─ engineering_reference.file_embeddings
        │
        └─ EmbeddingAdminController
             ├─ soft deprecate/restore by module
             ├─ asynchronously regenerate embeddings
             └─ permanently delete module data
```

### Architecture Notes

- API authentication is stateless; the service does not create HTTP sessions.
- The async executor is configured through `spring.task.execution.*` and copies MDC context into worker threads.
- Each embedding input includes a generated header containing path, file type, module, chunk position, and deprecation state.
- Canonical records are looked up by the null-safe tuple `(module, moduleVersion, path)` and then inserted or updated.
- Chunk records reference their canonical file through `canonical_file_id`.
- Re-embedding after a deprecation change runs asynchronously. The PATCH request returns before all vectors are necessarily regenerated.
- Semantic search embeds each query with the configured deployment and ranks stored chunks by pgvector cosine similarity.

---

## 🧩 Core Components

### `FileUploadController`

- Exposes `POST /api/files/upload`
- Accepts one or more regular files or ZIP archives
- Validates required version metadata and supported file types
- Derives ZIP module/version information from Maven or Gradle metadata
- Returns accepted and rejected upload names with HTTP `202`

### `FileStorageService`

- Creates the configured upload directory at startup
- Sanitizes uploaded names to their base name and extension
- Adds a unique suffix before saving
- Prevents writes outside the configured storage root

The storage location is temporary staging space. Processing removes each local file in a `finally` block.

### `ProcessingService`

- Reads files as UTF-8 text
- Upserts canonical file content and repository metadata
- Splits content using the model tokenizer
- Caps the configured chunk window at 8,191 tokens
- Generates and persists one vector per chunk
- Deletes staged files after completion or failure

### `EmbeddingService`

- Calls the configured Azure OpenAI embedding deployment
- Converts the provider's `List<Float>` result to `float[]`
- Rejects blank input and fails when the provider returns no embedding data

### `EmbeddingSearchController`

- Exposes `POST /api/embeddings/search` for agent and application retrieval
- Returns ranked chunks with cosine similarity scores and source metadata
- Supports optional module, module-version, and deprecated-content filters

### `EmbeddingAdminController`

- Changes the deprecation state of all embedding chunks for a module
- Triggers asynchronous vector regeneration so the deprecation metadata is reflected in embedding input
- Permanently deletes a module's embeddings and canonical files

### `ZipUtil`

- Prevents ZIP-slip path traversal
- Skips unsupported and oversized entries
- Enforces per-entry, total-size, and entry-count limits
- Extracts through temporary partial files before atomically moving completed entries

### Persistence Model

- `CanonicalFile` stores complete source content and repository metadata.
- `FileEmbedding` stores an individual content chunk and its generated vector.
- `FloatArrayVectorConverter` maps Java `float[]` values to PostgreSQL's vector-compatible textual representation.

---

## ✅ Prerequisites

- Java 21+
- Maven Wrapper (`./mvnw`) or Maven 3.9+
- PostgreSQL 15+ with:
  - `vector` extension
  - `pgcrypto` extension for `gen_random_uuid()` if database defaults use it
- An existing `engineering_reference` schema and application tables
- Azure OpenAI resource with an embedding deployment
- Keycloak-compatible OIDC issuer and access tokens containing the required roles

---

## ⚙️ Configuration

Primary configuration file:

```text readme.md
src/main/resources/application.yaml
```

The application uses the following environment variables directly:

| Environment variable | Purpose | Required/default |
|---|---|---|
| `pg_db_url` | PostgreSQL JDBC URL | Required |
| `pg_db_user` | PostgreSQL username | Required |
| `pg_db_pass` | PostgreSQL password | Required |
| `oai_api_endpoint` | Azure OpenAI endpoint | Required |
| `oai_api_key` | Azure OpenAI API key | Required |
| `oai_embedding_model_deployment` | Azure OpenAI embedding deployment name | Required |
| `keycloak_issuer_uri` | JWT issuer URI | Optional; falls back to `engineering_assistant_issuer_uri`, then the configured non-production URI |
| `engineering_assistant_issuer_uri` | Secondary JWT issuer fallback | Optional |
| `swagger_oauth_client_id` | Swagger UI OAuth client ID | Defaults to `embedding-service` |
| `root_log_level` | Root log level | Defaults to `INFO` |
| `hibernate_log_level` | `org.hibernate.SQL` log level | Defaults to `INFO` |
| `service_log_level` | Application package log level | Defaults to `INFO` |

> Never commit database credentials or Azure OpenAI API keys. Supply secrets through environment variables backed by an approved secret manager.

### Main application settings

| Property | Current value | Description |
|---|---:|---|
| `server.port` | `8080` | HTTP port |
| `spring.servlet.multipart.max-file-size` | `100MB` | Maximum individual multipart file size |
| `spring.servlet.multipart.max-request-size` | `100MB` | Maximum complete multipart request size |
| `spring.jpa.hibernate.ddl-auto` | `none` | Schema changes are not performed at startup |
| `spring.jpa.properties.hibernate.default_schema` | `engineering_reference` | Default persistence schema |
| `storage.location` | `${user.home}/file-ingestor/uploads` | Temporary upload staging directory |
| `vector.include-path-depth` | `2` | Number of trailing path segments retained; `0` keeps only the file name |
| `vector.chunk-size-tokens` | `800` | Token window; inherited from the configuration class default |
| `vector.chunk-overlap-tokens` | `100` | Token overlap; inherited from the configuration class default |
| `vector.max-chunks-per-file` | `100` | Maximum chunks persisted per file; `0` would mean unlimited |

Chunk size must be greater than zero. Chunk overlap must be non-negative and smaller than chunk size. The effective chunk window is capped at the model limit of 8,191 tokens.

The async executor currently uses four core threads, up to sixteen threads, and a queue capacity of 1,000. These values can be overridden through standard Spring Boot property binding.

---

## 🔐 Security

The service runs as an OAuth2 resource server and expects a bearer JWT:

```http readme.md
Authorization: Bearer <keycloak-access-token>
```

Roles are read from both of these Keycloak claim locations:

- `realm_access.roles`
- `resource_access.<client>.roles` for every client entry

Each role is mapped to a Spring authority with the `ROLE_` prefix. For example, `embedding-admin` becomes `ROLE_embedding-admin`.

Recognized application roles are:

- `embedding-user`
- `embedding-admin`
- `assistant-admin`

### Effective endpoint authorization

| Endpoint group | Effective access |
|---|---|
| `POST /api/files/upload` | `embedding-user`, `embedding-admin`, or `assistant-admin` |
| `POST /api/embeddings/search` | `embedding-user`, `embedding-admin`, or `assistant-admin` |
| Other `/api/**` endpoints | `embedding-admin` or `assistant-admin` |
| Non-API resources, including Swagger UI and OpenAPI JSON | Publicly reachable |

> Swagger UI being publicly reachable does not make protected API operations public. Calling an `/api/**` endpoint still requires a valid bearer token and one of the roles allowed for that endpoint.

CSRF is disabled because the API is stateless and bearer-token based.

---

## 🗄 Database

Hibernate uses the `engineering_reference` schema and has automatic DDL generation disabled. Database objects must therefore exist before the service starts.

### Logical data model

#### `engineering_reference.canonical_files`

Stores one canonical source record per module, version, and retained path, including:

- File name and retained path
- Module and module version
- File type
- Repository clone URL, repository reference, and path in the repository
- Complete source content
- Deprecation state
- Creation and update timestamps

A null-safe unique index on `(module, COALESCE(module_version, ''), path)` is recommended to match the application's upsert lookup.

#### `engineering_reference.file_embeddings`

Stores one row per processed chunk, including:

- File, path, module, version, and type metadata
- Chunk content and chunk position
- Deprecation state
- pgvector embedding
- Foreign key to `canonical_files`

A cosine-distance pgvector index is recommended for downstream retrieval workloads. Its vector dimension must match the configured Azure OpenAI embedding deployment.

### Schema bootstrap status

The repository contains `src/main/resources/schema.sql`, but SQL initialization is disabled with `spring.sql.init.mode=never`. The script also references `ingestor_db` in some statements while the JPA entities and runtime configuration use `engineering_reference`, and its current statement ordering should be reviewed before execution.

Do **not** assume that the checked-in script runs automatically or apply it unchanged to production. Provision and migrate `engineering_reference` through the environment's approved migration process, ensuring that table names, foreign keys, indexes, and vector dimensions match the deployed model.

Required PostgreSQL extensions typically include:

```sql readme.md
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

Extension creation may require elevated database privileges and should normally be handled by a database administrator or infrastructure migration.

---

## 🏃 Running Locally

### 1. Configure dependencies

Set the required database and Azure OpenAI values, plus an issuer URI when the configured fallback is not appropriate:

```bash readme.md
export pg_db_url='jdbc:postgresql://localhost:5432/engineering'
export pg_db_user='engineering_user'
export pg_db_pass='<database-password>'
export oai_api_endpoint='https://<resource>.openai.azure.com/'
export oai_api_key='<azure-openai-key>'
export oai_embedding_model_deployment='<embedding-deployment>'
export keycloak_issuer_uri='https://<keycloak-host>/realms/<realm>'
```

Provision the database schema before starting the application.

### 2. Build and test

```bash readme.md
./mvnw clean verify
```

### 3. Run

```bash readme.md
./mvnw spring-boot:run
```

Alternatively, run the packaged application:

```bash readme.md
./mvnw clean package
java -jar target/engineering-data-service-1.0.0-SNAPSHOT.jar
```

### 4. Access API documentation

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Use Swagger UI's OAuth authorization flow or provide a bearer token from another API client.

---

## 🔌 REST API

All API calls use the `/api` prefix. Upload processing and module re-embedding are dispatched asynchronously; hard deletion is synchronous.

### Upload files

```http readme.md
POST /api/files/upload
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

Multipart parts:

| Part | Required | Description |
|---|---:|---|
| `files` | Yes | One or more files or ZIP archives |
| `module` | No | Module name. For ZIPs, use `from-package` or omit it to infer from Maven/Gradle metadata |
| `fileVersion` | Required for regular files | Version assigned to the canonical file and chunks; ZIPs may infer it from Maven/Gradle metadata |
| `repoCloneUrl` | No | Source repository clone URL stored with the canonical file |
| `repoRef` | No | Branch, tag, or commit reference; defaults to `master` during processing |
| `pathInRepo` | No | Source path within the repository |

Regular files without `module` use `undefined`. Regular files require a non-blank `fileVersion`.

For ZIP uploads, version resolution follows this order:

1. Version from the first matching Maven POM
2. Version from a matching Gradle build file
3. Submitted `fileVersion`

ZIP module resolution follows this order unless an explicit module other than `from-package` is submitted:

1. Maven `artifactId`
2. Gradle `rootProject.name`
3. `undefined`

Example:

```bash readme.md
curl --request POST 'http://localhost:8080/api/files/upload' \
  --header 'Authorization: Bearer <token>' \
  --form 'files=@src/main/java/example/PaymentService.java' \
  --form 'module=payments' \
  --form 'fileVersion=1.4.0' \
  --form 'repoCloneUrl=https://git.example.com/platform/payments.git' \
  --form 'repoRef=main' \
  --form 'pathInRepo=src/main/java/example/PaymentService.java'
```

Successful submission returns `202 Accepted`:

```json readme.md
{
  "accepted": [
    "PaymentService.java"
  ],
  "rejected": []
}
```

A mixed request may contain both accepted and rejected files:

```json readme.md
{
  "accepted": [
    "payments.zip"
  ],
  "rejected": [
    "notes.xyz (unsupported type)",
    "Empty.java (empty)"
  ]
}
```

The response reports request-level validation and staging results. Inspect application logs to determine whether asynchronous processing later succeeded.

### Semantic embedding search

An agent can use this endpoint as a retrieval tool. The service embeds the natural-language query with the same Azure OpenAI deployment used for ingestion, then returns the nearest stored chunks by cosine similarity.

```http readme.md
POST /api/embeddings/search
Content-Type: application/json
Authorization: Bearer <token>
```

Request fields:

| Field | Required | Description |
|---|---:|---|
| `query` | Yes | Non-blank natural-language or code search query |
| `limit` | No | Number of results from 1 to 50; defaults to 10 |
| `module` | No | Exact module filter |
| `moduleVersion` | No | Exact module-version filter |
| `includeDeprecated` | No | Include deprecated chunks; defaults to `false` |

Example:

```bash readme.md
curl --request POST 'http://localhost:8080/api/embeddings/search' \
  --header 'Authorization: Bearer <token>' \
  --header 'Content-Type: application/json' \
  --data '{
    "query": "Where is payment authentication configured?",
    "limit": 5,
    "module": "payments"
  }'
```

The response is a JSON array ordered from most to least similar. Each result contains `score`, `content`, `fileName`, `path`, `module`, `moduleVersion`, `fileType`, `chunkIndex`, `chunkCount`, and `deprecated`. `score` is `1 - cosine_distance`; larger values are more similar.

The agent must send a Keycloak bearer token containing `embedding-user`, `embedding-admin`, or `assistant-admin`.

### Deprecate or restore a module

```http readme.md
PATCH /api/embeddings/module/{module}?deprecated=true|false
Authorization: Bearer <token>
```

Example:

```bash readme.md
curl --request PATCH \
  'http://localhost:8080/api/embeddings/module/payments?deprecated=true' \
  --header 'Authorization: Bearer <token>'
```

The operation updates the deprecation flag on all matching embedding rows and then starts asynchronous re-embedding. A successful response is plain text:

```text readme.md
updated: 42
```

Restoration uses `deprecated=false`.

> The current operation changes `file_embeddings.deprecated`; it does not update the corresponding `canonical_files.deprecated` values.

### Permanently delete a module

```http readme.md
DELETE /api/embeddings/module/{module}
Authorization: Bearer <token>
```

Example:

```bash readme.md
curl --request DELETE \
  'http://localhost:8080/api/embeddings/module/payments' \
  --header 'Authorization: Bearer <token>'
```

This transaction deletes embedding rows first and canonical file rows second. The operation is irreversible.

Successful response:

```text readme.md
deleted all rows for module: payments
```

---

## 🔄 Processing Behavior

### Chunking

- Input is decoded as UTF-8.
- Blank content is skipped.
- The default chunk window is 800 tokens with a 100-token overlap.
- The configured window cannot exceed 8,191 tokens.
- At most 100 chunks are processed per file with the current configuration.
- If a file exceeds the maximum, only the first configured number of chunks are embedded and the truncation is logged.

Each initial embedding input is prefixed with metadata similar to:

```text readme.md
### path: service/PaymentService.java
### type: JAVA
### module: payments
### chunk: 1/4
### deprecated: false
###
```

Re-embedding uses path, type, module, and the current deprecation state in its metadata header.

### Canonical-file behavior

Uploading the same retained path for the same module and module version updates the canonical file. Chunk embeddings are inserted by the current processing implementation; existing chunk rows are not automatically deleted as part of that upsert.

### Path retention

`vector.include-path-depth` controls the number of trailing path segments retained. With the current value of `2`, a longer path may be stored as:

```text readme.md
service/PaymentService.java
```

A value of `0` stores only the file name.

### Failure handling

- Individual invalid files are added to the upload response's `rejected` list.
- Controller-level unexpected errors return `500` with `{"message":"Internal error"}`.
- Missing multipart parts return `400`.
- Multipart size violations return `413`.
- Failures after asynchronous dispatch are logged and are not reflected in the original `202` response.
- Temporary files are deleted after processing, including on processing failure.

---

## ⚙️ Operations and Troubleshooting

### Monitor asynchronous ingestion

Use structured log events and the generated `requestId` to follow a request. Useful events include:

- `upload_start` / `upload_complete`
- `file_saved` / `file_rejected`
- `canonical_file_upserted`
- `chunk_persisted`
- `processing_complete` / `processing_error`
- `reembed_start` / `reembed_complete`
- `zip_entry_skipped` / `zip_extract_stopped`

### Upload accepted but no vectors appear

A `202` only confirms dispatch. Check logs for:

- Invalid Azure OpenAI endpoint, key, or deployment name
- Database connectivity or schema mismatch
- Vector dimension mismatch
- Invalid chunk settings
- Empty or non-UTF-8 content
- Async executor saturation

### PostgreSQL reports a vector type error

Verify that:

- The `vector` extension is installed.
- The `embedding` column uses the dimension returned by the configured model.
- The application is connected to the intended `engineering_reference` schema.
- The JDBC and pgvector dependencies match the deployed PostgreSQL setup.

### ZIP content is missing

Entries can be skipped because they are unsupported, exceed 512 KiB uncompressed, or occur after the archive reaches its 10 MiB/10,000-entry limits. Review `zip_entry_skipped` and `zip_extract_stopped` events.

### Module deprecation appears partially updated

The database flag update is synchronous, but vector regeneration is asynchronous and performed row by row. Check `reembed_*` logs for completion or per-row failures.

### Build and test

```bash readme.md
./mvnw clean verify
```

The test profile uses an in-memory H2 database in PostgreSQL compatibility mode and does not call a production PostgreSQL instance.

---

## 📜 License

Licensed under the [Apache License, Version 2.0](LICENSE). You may use, reproduce, and distribute this project in accordance with the license terms.

Azure OpenAI usage remains subject to the applicable Microsoft terms and responsible AI requirements.
