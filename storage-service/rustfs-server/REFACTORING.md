# Storage Service Refactoring Documentation

## Overview

This document describes the enterprise-level refactoring applied to the storage-service/rustfs-server module. The refactoring follows Java best practices while maintaining all existing functionality.

## Refactoring Summary

### 1. Configuration Properties Pattern

**Before:**
- Scattered `@ConfigProperty` annotations throughout codebase
- Hard to discover all configuration options
- No type safety for related configurations

**After:**
- Centralized `StorageProperties` interface with `@ConfigMapping`
- Hierarchical configuration structure (storage.s3.*, storage.presigned-url.*, storage.circuit-breaker.*)
- Compile-time type safety and IDE autocomplete support
- Single source of truth for all storage configuration

**Files:**
- NEW: `/src/main/java/com/chatbot/storage/config/StorageProperties.java`
- UPDATED: `application.yml` - restructured configuration

**Benefits:**
- Easy configuration discovery
- Type-safe configuration access
- Better documentation through structured interfaces
- Simplified testing with configuration overrides

### 2. Storage Abstraction Layer

**Before:**
- Direct coupling to AWS SDK (S3Client, S3Presigner)
- Misleading naming suggesting AWS-only support
- Difficult to test or swap storage backends

**After:**
- Storage-agnostic `ObjectStorageClient` interface
- `S3ObjectStorageClient` implementation (works with any S3-compatible storage)
- Clear separation between interface and implementation
- Adapter pattern for AWS SDK

**Files:**
- NEW: `/src/main/java/com/chatbot/storage/client/ObjectStorageClient.java` - Interface
- NEW: `/src/main/java/com/chatbot/storage/client/S3ObjectStorageClient.java` - Implementation
- NEW: `/src/main/java/com/chatbot/storage/config/ObjectStorageConfiguration.java` - Bean producer
- DELETED: `/src/main/java/com/chatbot/storage/config/S3PresignerProducer.java` - Replaced
- UPDATED: `/src/main/java/com/chatbot/storage/service/StorageService.java` - Uses abstraction

**Benefits:**
- Storage backend independence (works with RustFS, MinIO, AWS S3, Ceph, etc.)
- Improved testability with mock implementations
- Clear separation of concerns
- Future-proof for storage backend changes

### 3. Enhanced Documentation

**Before:**
- Minimal inline documentation
- No architectural documentation

**After:**
- Comprehensive JavaDoc on all public APIs
- Architecture decision rationale in comments
- Clear usage examples in Docker Compose

**Benefits:**
- Easier onboarding for new developers
- Self-documenting code
- Better IDE support with inline documentation

### 4. Native Image Support

**Before:**
- No Docker support

**After:**
- `Dockerfile.jvm` - Optimized JVM-based container
- `Dockerfile.native` - GraalVM native image for faster startup
- `.dockerignore` - Optimized build context
- `docker-compose.yml` - Complete local development environment

**Files:**
- NEW: `Dockerfile.jvm`
- NEW: `Dockerfile.native`
- NEW: `.dockerignore`
- NEW: `docker-compose.yml`

**Benefits:**
- Production-ready container images
- Multi-stage builds for minimal image size
- Native image option for <100ms startup time
- Complete local development environment with Docker Compose

## Architecture

### Layered Architecture

```
┌─────────────────────────────────────────┐
│         REST Resources Layer            │  (FileResource.java)
│    - HTTP endpoints                     │
│    - Request/response DTOs              │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│         Service Layer                   │  (StorageService.java)
│    - Business logic                     │
│    - Circuit breakers                   │
│    - Event emission                     │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│      Storage Client Abstraction         │  (ObjectStorageClient interface)
│    - Storage operations interface       │
│    - Backend agnostic                   │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│    S3-Compatible Implementation         │  (S3ObjectStorageClient)
│    - AWS SDK adapter                    │
│    - Works with any S3-compatible       │
└─────────────────────────────────────────┘
```

### Configuration Hierarchy

```yaml
storage:
  bucket-name: "storage"

  s3:
    endpoint: "http://localhost:9000"
    region: "us-east-1"
    access-key-id: "minioadmin"
    secret-access-key: "minioadmin"
    path-style-access: true

  presigned-url:
    expiration: "1h"

  circuit-breaker:
    request-volume-threshold: 4
    delay: "5s"
    success-threshold: 2
```

## Maintained Functionality

All existing features are preserved:

1. **File Operations**
   - Upload with streaming (multipart/form-data)
   - Download with proper content-type headers
   - Delete operations
   - Metadata retrieval

2. **Advanced Features**
   - Presigned URLs for temporary access
   - Object tagging and metadata
   - Search with prefix and MIME type filters
   - Automatic bucket creation on startup

3. **Fault Tolerance**
   - Circuit breaker pattern on all storage operations
   - Configurable thresholds and delays
   - Failure ratio monitoring

4. **Event-Driven Architecture**
   - Kafka event emission for file created/deleted
   - Asynchronous event processing
   - JSON serialization

## Migration Guide

### For Developers

No code changes required. The refactoring is backward compatible:

1. Update configuration in `application.yml` to use new structure (optional, old config still works via Quarkus auto-config)
2. Rebuild the application: `./gradlew :storage-service:rustfs-server:build`
3. Run tests: `./gradlew :storage-service:rustfs-server:test`

### For Operations

**Running with Docker (JVM):**
```bash
cd storage-service/rustfs-server
docker-compose --profile jvm up
```

**Running with Docker (Native):**
```bash
cd storage-service/rustfs-server
docker-compose --profile native up
```

**Local Development:**
```bash
# Start infrastructure only
docker-compose up minio redpanda

# Run application locally
./gradlew :storage-service:rustfs-server:quarkusDev
```

## Testing

### Unit Tests
Circuit breaker and business logic can now be tested with mock `ObjectStorageClient`:

```java
@InjectMock
ObjectStorageClient mockStorageClient;

@Test
void testUpload() {
    // Mock storage operations
    // Test business logic in isolation
}
```

### Integration Tests
Use Testcontainers with MinIO:

```java
@QuarkusTestResource(MinioTestResource.class)
class StorageServiceIT {
    // Tests against real S3-compatible storage
}
```

## Performance Characteristics

### JVM Image
- Startup time: ~2-3 seconds
- Memory footprint: ~200-300MB
- Image size: ~150-200MB

### Native Image
- Startup time: ~50-100ms
- Memory footprint: ~50-100MB
- Image size: ~100-150MB
- Build time: ~5-10 minutes

## S3-Compatible Storage Backends

This service works with any S3-compatible storage:

| Backend  | Use Case              | Notes                          |
|----------|-----------------------|--------------------------------|
| RustFS   | Local development     | Lightweight, fast              |
| MinIO    | Development/Staging   | Full S3 compatibility          |
| AWS S3   | Production (cloud)    | Managed service, global scale  |
| Ceph     | On-premise production | Self-hosted, distributed       |

## Security Considerations

1. **Credentials Management**
   - Use environment variables for production
   - Never commit credentials to source control
   - Consider AWS IAM roles for EKS/EC2 deployments

2. **Network Security**
   - TLS/HTTPS for production endpoints
   - Network policies for Kubernetes deployments
   - Private VPC for storage backends

3. **Access Control**
   - Presigned URLs with configurable expiration
   - Future: implement IAM-based access control
   - Future: virus scanning integration

## Future Enhancements

1. **Multi-Backend Support**
   - Add Azure Blob Storage implementation
   - Add Google Cloud Storage implementation
   - Runtime backend selection via configuration

2. **Advanced Features**
   - Multipart upload for large files (>5GB)
   - Server-side encryption configuration
   - Object versioning support
   - Lifecycle policies

3. **Observability**
   - Distributed tracing with OpenTelemetry
   - Custom metrics for storage operations
   - Detailed error categorization

4. **Performance**
   - Caching layer for metadata
   - Connection pooling optimization
   - Async/reactive API option

## References

- [Quarkus Configuration Guide](https://quarkus.io/guides/config-reference)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [MinIO S3 Compatibility](https://min.io/docs/minio/linux/developers/s3-compatible.html)
- [Quarkus Native Image](https://quarkus.io/guides/building-native-image)

## Changelog

### Version 2.0 (Current Refactoring)

**Added:**
- Storage abstraction layer (ObjectStorageClient)
- Configuration properties pattern (StorageProperties)
- Native image support (Dockerfile.native)
- Docker Compose for local development
- Comprehensive documentation

**Changed:**
- Refactored StorageService to use abstraction
- Consolidated configuration management
- Enhanced JavaDoc coverage

**Removed:**
- S3PresignerProducer (replaced by ObjectStorageConfiguration)
- Scattered @ConfigProperty annotations

**Maintained:**
- All existing REST API endpoints
- Circuit breaker fault tolerance
- Kafka event emission
- File upload/download streaming
- Presigned URL generation
