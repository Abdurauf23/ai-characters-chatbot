# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Characters Chatbot is a microservices-based application built with Quarkus. The project uses Gradle with Kotlin DSL for build automation and follows a modular architecture with distinct services.

## Build System

This is a Gradle multi-module project using Kotlin DSL (`.gradle.kts`).

**Core Commands:**
```bash
# Build entire project
./gradlew build

# Build specific module
./gradlew :storage-service:rustfs-server:build

# Run Quarkus dev mode (hot reload)
./gradlew :storage-service:rustfs-server:quarkusDev

# Format code
./gradlew spotlessApply

# Check code formatting
./gradlew spotlessCheck

# Clean build artifacts
./gradlew clean
```

## Code Style

- **Spotless** is configured project-wide for code formatting
- Uses **Google Java Format** for Java files
- Automatically removes unused imports and forbids wildcard imports
- Format check runs before builds (via `spotlessCheck` dependency)
- Always run `./gradlew spotlessApply` before committing changes

## Architecture

### Module Structure

- **storage-service/rustfs-server**: Main storage service implementation (Quarkus-based)
- **storage-service/rustfs-client**: Client library (currently commented out in build)
- **kong-gateway**: API Gateway configuration

### Storage Service (rustfs-server)

**Technology Stack:**
- Quarkus 3.31.2 (Jakarta EE, MicroProfile)
- AWS S3 SDK for object storage
- Kafka/Redpanda for event streaming
- SmallRye for reactive messaging and fault tolerance

**Key Components:**

1. **REST API Layer** (`FileResource.java`):
   - File upload with multipart form data
   - File download with streaming
   - Presigned URL generation
   - Metadata search and updates
   - Base path: `/v1/files`

2. **Storage Layer** (`StorageService.java`):
   - S3-compatible object storage integration
   - Circuit breaker pattern on all S3 operations (threshold: 4 requests)
   - Auto-creates bucket on startup if missing
   - Files identified by UUID, stored as object keys

3. **Event System** (`StorageEventEmitter.java`, `FileEvent.java`):
   - Publishes events to Kafka topic `storage.events`
   - Event types: `FileCreated`, `FileDeleted`
   - Uses MicroProfile Reactive Messaging

**File Storage Model:**
- Files are stored with UUID as object key
- Metadata includes: filename, mime type, size, creation timestamp
- Tags are stored as S3 object tags (separate from metadata)
- Presigned URLs expire after 1 hour

### Infrastructure

**Local Development Services:**

1. **RustFS** (S3-compatible storage):
   - Runs on ports 9000 (API) and 9001 (console)
   - Start: `docker-compose -f storage-service/docker-compose.yml up`
   - Credentials: `rustfsadmin` / `rustfsadmin`

2. **Kong Gateway**:
   - Proxy: port 8000
   - Admin API: port 8001
   - Admin GUI: port 8002
   - Start: `docker-compose -f kong-gateway/docker-compose.yml up`
   - Configuration: `kong-gateway/kong.yaml` (declarative)

**Configuration (`application.yml`):**
- S3 endpoint: `http://localhost:9000` (path-style access enabled)
- Kafka: `localhost:19092`
- Service port: 8080
- CORS enabled for all origins

## Development Workflow

1. Start infrastructure:
   ```bash
   docker-compose -f storage-service/docker-compose.yml up -d
   docker-compose -f kong-gateway/docker-compose.yml up -d
   ```

2. Run service in dev mode:
   ```bash
   ./gradlew :storage-service:rustfs-server:quarkusDev
   ```

3. Format code before committing:
   ```bash
   ./gradlew spotlessApply
   ```

## Dependencies

Managed via `gradle/libs.versions.toml`:
- Quarkus platform BOM for version alignment
- AWS SDK through Quarkiverse Amazon Services
- Key extensions: REST Jackson, Kafka messaging, OpenAPI, Fault Tolerance
- URL connection client for synchronous S3 operations

## Important Notes

- The `rustfs-client` module is currently commented out in the build configuration
- Circuit breaker pattern protects all S3 operations to prevent cascading failures
- Storage service auto-creates the S3 bucket on first startup
- All file operations are instrumented with OpenAPI documentation (SmallRye OpenAPI)
