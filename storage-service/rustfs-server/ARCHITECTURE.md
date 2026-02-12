# Storage Service Architecture

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Clients                              │
│                  (Browser, Mobile Apps, APIs)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │ REST API
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Storage Service                              │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Resource Layer (REST)                       │   │
│  │  - FileResource.java                                     │   │
│  │  - HTTP endpoints (/v1/files/*)                         │   │
│  │  - Request validation & DTOs                            │   │
│  └──────────────────────┬──────────────────────────────────┘   │
│                         │                                        │
│  ┌──────────────────────▼──────────────────────────────────┐   │
│  │              Service Layer                               │   │
│  │  - StorageService.java                                   │   │
│  │  - Business logic                                        │   │
│  │  - Circuit breaker (@CircuitBreaker)                    │   │
│  │  - Event emission                                        │   │
│  └──────┬───────────────────────────────────────┬──────────┘   │
│         │                                        │               │
│  ┌──────▼─────────────────────────┐   ┌────────▼─────────┐    │
│  │  Storage Client Abstraction    │   │  Event Emitter   │    │
│  │  - ObjectStorageClient         │   │  - Kafka         │    │
│  │  - Vendor-neutral interface    │   │  - storage.events│    │
│  └──────┬─────────────────────────┘   └──────────────────┘    │
│         │                                                        │
│  ┌──────▼─────────────────────────┐                            │
│  │  S3-Compatible Implementation  │                            │
│  │  - S3ObjectStorageClient       │                            │
│  │  - AWS SDK adapter             │                            │
│  └──────┬─────────────────────────┘                            │
│         │                                                        │
│  ┌──────▼─────────────────────────┐                            │
│  │    Configuration                │                            │
│  │  - StorageProperties            │                            │
│  │  - ObjectStorageConfiguration   │                            │
│  └─────────────────────────────────┘                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼──────┐  ┌──────▼──────┐  ┌─────▼────────┐
│   RustFS     │  │    MinIO    │  │   AWS S3     │
│   (local)    │  │  (staging)  │  │ (production) │
│              │  │             │  │              │
│ S3-Compatible│  │S3-Compatible│  │S3-Compatible │
└──────────────┘  └─────────────┘  └──────────────┘
```

## Component Interactions

```
File Upload Flow:
─────────────────

Client Request
     │
     ├─→ FileResource.upload()
     │       │
     │       ├─→ Parse multipart form-data
     │       └─→ StorageService.upload()
     │               │
     │               ├─→ Generate UUID for file
     │               ├─→ Create metadata map
     │               └─→ ObjectStorageClient.putObject()
     │                       │
     │                       ├─→ S3ObjectStorageClient
     │                       └─→ S3Client (AWS SDK)
     │                               │
     │                               └─→ S3-Compatible Storage
     │
     └─→ StorageEventEmitter.fileCreated()
             │
             └─→ Kafka Topic (storage.events)


File Download Flow:
───────────────────

Client Request
     │
     ├─→ FileResource.getFile(fileId)
     │       │
     │       ├─→ StorageService.getFileMeta(fileId)
     │       │       │
     │       │       └─→ ObjectStorageClient.getObjectMetadata()
     │       │
     │       └─→ StorageService.download(fileId)
     │               │
     │               └─→ ObjectStorageClient.getObject()
     │                       │
     │                       └─→ S3-Compatible Storage
     │
     └─→ Stream response to client


Presigned URL Flow:
───────────────────

Client Request
     │
     ├─→ FileResource.getPresignedUrl(fileId)
     │       │
     │       └─→ StorageService.generatePresignedUrl(fileId)
     │               │
     │               └─→ ObjectStorageClient.generatePresignedGetUrl()
     │                       │
     │                       └─→ S3Presigner (AWS SDK)
     │
     └─→ Return presigned URL to client
```

## Circuit Breaker Pattern

```
┌────────────────────────────────────────────────┐
│        Circuit Breaker State Machine           │
│                                                 │
│                   CLOSED                        │
│                     │                           │
│         Success ────┘                           │
│              │                                  │
│         requestVolumeThreshold                  │
│         failures (4 consecutive)                │
│              │                                  │
│              ↓                                  │
│                    OPEN                         │
│         (Reject all requests)                   │
│              │                                  │
│         Wait delay period (5s)                  │
│              │                                  │
│              ↓                                  │
│                 HALF-OPEN                       │
│         (Allow test requests)                   │
│              │                                  │
│    successThreshold (2)     │   Failure         │
│    consecutive successes    │                   │
│              │              │                   │
│              ↓              ↓                   │
│           CLOSED          OPEN                  │
│                                                 │
└────────────────────────────────────────────────┘

Applied to all StorageService methods:
- upload()
- getFileMeta()
- download()
- delete()
- generatePresignedUrl()
- search()
- updateTags()
```

## Configuration Hierarchy

```
storage:
  │
  ├── bucket-name: "storage"
  │
  ├── s3:
  │   ├── endpoint: "http://localhost:9000"
  │   ├── region: "us-east-1"
  │   ├── access-key-id: "minioadmin"
  │   ├── secret-access-key: "minioadmin"
  │   └── path-style-access: true
  │
  ├── presigned-url:
  │   └── expiration: "1h"
  │
  └── circuit-breaker:
      ├── request-volume-threshold: 4
      ├── delay: "5s"
      └── success-threshold: 2
```

## Data Flow

### File Upload
```
1. Client sends multipart/form-data
   └─→ file: binary data
   └─→ tags: JSON {"env":"dev","type":"document"}

2. Service generates UUID and metadata
   └─→ fileId: UUID.randomUUID()
   └─→ objectKey: fileId.toString()
   └─→ metadata: {"filename": "test.txt", "created-at": "2026-02-09T..."}

3. Storage client stores object
   └─→ bucket: "storage"
   └─→ key: "123e4567-e89b-12d3-a456-426614174000"
   └─→ content-type: "text/plain"
   └─→ metadata: {...}
   └─→ tags: {...}

4. Event emitted to Kafka
   └─→ topic: "storage.events"
   └─→ event: FileCreated {...}

5. Response returned
   └─→ {"fileId": "...", "objectKey": "..."}
```

### Presigned URL Generation
```
1. Client requests presigned URL
   └─→ GET /v1/files/{fileId}/presigned-url

2. Service generates presigned URL
   └─→ Uses S3Presigner with configured expiration
   └─→ Signs URL with storage credentials
   └─→ Valid for configured duration (1h default)

3. Response contains URL and expiration
   └─→ {"url": "http://...", "expiresAt": "2026-02-09T..."}

4. Client can use URL directly
   └─→ No authentication required
   └─→ Time-limited access
   └─→ Can be shared or embedded
```

## Error Handling

```
┌─────────────────────────────────────────┐
│          Error Handling Flow             │
│                                          │
│  Storage Operation (e.g., putObject)     │
│           │                              │
│           ├─→ Try operation              │
│           │       │                      │
│           │       ├─→ Success → Return   │
│           │       │                      │
│           │       └─→ S3Exception        │
│           │               │              │
│           │               ↓              │
│           │   ObjectStorageException     │
│           │   (wrapped with context)     │
│           │               │              │
│           │               ↓              │
│           │       Circuit Breaker        │
│           │       (track failure)        │
│           │               │              │
│           │               ↓              │
│           │     Log error details        │
│           │               │              │
│           │               ↓              │
│           │   Propagate to service       │
│           │               │              │
│           │               ↓              │
│           │   StorageExceptionMapper     │
│           │   (converts to HTTP status)  │
│           │               │              │
│           │               ↓              │
│           │   Return error response      │
│           │   to client                  │
└─────────────────────────────────────────┘
```

## Deployment Architecture

### Local Development
```
┌──────────────┐
│  Developer   │
│   Machine    │
├──────────────┤
│ Quarkus Dev  │◄─── ./gradlew quarkusDev
│   Mode       │
└──────┬───────┘
       │
       ├─→ MinIO (Docker)
       └─→ Redpanda (Docker)
```

### Docker Compose (Local Testing)
```
┌─────────────────────────────────────┐
│         Docker Compose              │
│                                     │
│  ┌───────────────┐                 │
│  │ Storage       │                 │
│  │ Service       │                 │
│  │ (JVM/Native)  │                 │
│  └───────┬───────┘                 │
│          │                          │
│          ├─→ MinIO Container        │
│          └─→ Redpanda Container     │
│                                     │
└─────────────────────────────────────┘
```

### Production (Kubernetes)
```
┌─────────────────────────────────────────────┐
│              Kubernetes Cluster              │
│                                              │
│  ┌──────────────────────────────────┐       │
│  │     Ingress / API Gateway        │       │
│  └────────────┬─────────────────────┘       │
│               │                              │
│  ┌────────────▼─────────────────────┐       │
│  │  Storage Service Deployment      │       │
│  │  (replicas: 3)                   │       │
│  │  - Pod 1: storage-service        │       │
│  │  - Pod 2: storage-service        │       │
│  │  - Pod 3: storage-service        │       │
│  └────────────┬─────────────────────┘       │
│               │                              │
│               ├─→ AWS S3 / MinIO (external) │
│               └─→ Kafka (external / cluster)│
│                                              │
└──────────────────────────────────────────────┘
```

## Security Architecture

```
┌────────────────────────────────────────┐
│         Security Layers                 │
│                                         │
│  1. Transport Layer                     │
│     - TLS/HTTPS (production)            │
│     - Certificate validation            │
│                                         │
│  2. Authentication                      │
│     - S3 credentials (access/secret)    │
│     - IAM roles (AWS EKS/EC2)          │
│                                         │
│  3. Authorization                       │
│     - Presigned URLs (time-limited)     │
│     - Bucket policies                   │
│                                         │
│  4. Data Protection                     │
│     - Encryption at rest (S3)           │
│     - Encryption in transit (TLS)       │
│                                         │
│  5. Input Validation                    │
│     - File size limits                  │
│     - MIME type validation              │
│     - Metadata sanitization             │
│                                         │
└─────────────────────────────────────────┘
```

## Observability

```
┌────────────────────────────────────────┐
│         Observability Stack             │
│                                         │
│  Logs                                   │
│  └─→ JBoss Logging                      │
│      └─→ Structured JSON (future)       │
│      └─→ Log levels: DEBUG, INFO, WARN  │
│                                         │
│  Metrics (Quarkus Built-in)             │
│  └─→ /q/metrics                         │
│  └─→ Circuit breaker state              │
│  └─→ HTTP request metrics               │
│                                         │
│  Health Checks                          │
│  └─→ /q/health/live                     │
│  └─→ /q/health/ready                    │
│                                         │
│  Events (Kafka)                         │
│  └─→ FILE_CREATED                       │
│  └─→ FILE_DELETED                       │
│                                         │
│  Future Enhancements:                   │
│  └─→ Distributed tracing (Jaeger)       │
│  └─→ Custom metrics (Micrometer)        │
│  └─→ APM integration (Elastic APM)      │
│                                         │
└─────────────────────────────────────────┘
```

## Abstraction Benefits

### Before (Tightly Coupled)
```
FileResource → StorageService → S3Client (AWS SDK)
                                    │
                                    └─→ Only works with AWS S3
                                        or S3-compatible with
                                        AWS SDK configuration
```

### After (Loosely Coupled)
```
FileResource → StorageService → ObjectStorageClient (Interface)
                                        │
                        ┌───────────────┼───────────────┐
                        │               │               │
              S3ObjectStorageClient  AzureBlob     GCSClient
                        │            (future)      (future)
                        │
                    AWS SDK
                        │
        ┌───────────────┼───────────────┬──────────┐
        │               │               │          │
    RustFS          MinIO            AWS S3      Ceph
```

## Technology Stack

```
┌─────────────────────────────────────────┐
│          Technology Layers               │
│                                          │
│  Web Framework                           │
│  └─→ Quarkus 3.31.2                     │
│      └─→ RESTEasy Reactive               │
│      └─→ Jackson (JSON)                  │
│                                          │
│  Fault Tolerance                         │
│  └─→ SmallRye Fault Tolerance            │
│      └─→ MicroProfile Fault Tolerance    │
│                                          │
│  Messaging                               │
│  └─→ SmallRye Reactive Messaging         │
│      └─→ Kafka Client                    │
│                                          │
│  Configuration                           │
│  └─→ SmallRye Config                     │
│      └─→ MicroProfile Config             │
│                                          │
│  Storage                                 │
│  └─→ AWS SDK for Java 2.x                │
│      └─→ S3 Client                       │
│      └─→ S3 Presigner                    │
│                                          │
│  Documentation                           │
│  └─→ SmallRye OpenAPI                    │
│      └─→ Swagger UI                      │
│                                          │
└──────────────────────────────────────────┘
```

## Design Patterns Applied

1. **Adapter Pattern**
   - `S3ObjectStorageClient` adapts AWS SDK to `ObjectStorageClient` interface

2. **Dependency Inversion Principle**
   - `StorageService` depends on `ObjectStorageClient` interface, not concrete implementation

3. **Configuration Mapping Pattern**
   - `StorageProperties` provides type-safe, hierarchical configuration

4. **Circuit Breaker Pattern**
   - Protects against cascading failures from storage backend

5. **Event-Driven Architecture**
   - Asynchronous event emission via Kafka for file operations

6. **Producer Pattern (CDI)**
   - `ObjectStorageConfiguration` produces configured beans

7. **Layered Architecture**
   - Clear separation: Resource → Service → Client → External System

8. **Repository Pattern (Variant)**
   - `ObjectStorageClient` provides CRUD-like operations for storage

---

**Version**: 2.0 (Post-Refactoring)
**Last Updated**: 2026-02-09
**Maintainer**: Java Architecture Team
