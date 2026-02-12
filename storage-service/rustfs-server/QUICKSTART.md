# Storage Service - Quick Start Guide

## 5-Minute Setup

### Option 1: Docker Compose (Recommended for First-Time Setup)

```bash
# Navigate to the service directory
cd storage-service/rustfs-server

# Start everything (infrastructure + application)
docker-compose --profile jvm up

# Wait for services to start (30-60 seconds)
# Access Swagger UI: http://localhost:8080/q/swagger-ui
```

### Option 2: Local Development

```bash
# Start infrastructure only
cd storage-service/rustfs-server
docker-compose up minio redpanda

# In another terminal, run the application
./gradlew :storage-service:rustfs-server:quarkusDev
```

## First API Call

### Upload a File
```bash
# Create a test file
echo "Hello, Storage Service!" > test.txt

# Upload it
curl -X POST http://localhost:8080/v1/files \
  -F "file=@test.txt" \
  -F 'tags={"env":"dev","type":"test"}'

# Response:
# {
#   "fileId": "123e4567-e89b-12d3-a456-426614174000",
#   "objectKey": "123e4567-e89b-12d3-a456-426614174000"
# }
```

### Download the File
```bash
# Replace {fileId} with the value from upload response
curl -O http://localhost:8080/v1/files/{fileId}
```

### Get Presigned URL
```bash
curl http://localhost:8080/v1/files/{fileId}/presigned-url

# Response:
# {
#   "url": "http://localhost:9000/storage/123e4567...?X-Amz-...",
#   "expiresAt": "2026-02-09T12:00:00Z"
# }
```

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Storage API | http://localhost:8080 | None |
| Swagger UI | http://localhost:8080/q/swagger-ui | None |
| Health Check | http://localhost:8080/q/health | None |
| Metrics | http://localhost:8080/q/metrics | None |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |
| Redpanda Admin | http://localhost:9644 | None |

## Common Tasks

### View All Files in MinIO
1. Open http://localhost:9001
2. Login with minioadmin/minioadmin
3. Browse to "storage" bucket

### Monitor Kafka Events
```bash
# Install Redpanda CLI (rpk) or use docker exec
docker exec -it storage-redpanda rpk topic consume storage.events

# Upload a file in another terminal to see events
```

### Check Circuit Breaker Status
```bash
curl http://localhost:8080/q/metrics | grep circuit
```

### View Application Logs
```bash
# Docker Compose
docker-compose logs -f storage-service-jvm

# Local dev
# Logs appear in terminal where gradlew is running
```

## Configuration

### Change Storage Endpoint
```yaml
# In application.yml
storage:
  s3:
    endpoint: "http://your-minio-server:9000"
    access-key-id: "your-access-key"
    secret-access-key: "your-secret-key"
```

### Adjust Circuit Breaker
```yaml
storage:
  circuit-breaker:
    request-volume-threshold: 10  # More failures before open
    delay: "10s"                  # Wait longer before retry
```

### Change Presigned URL Expiration
```yaml
storage:
  presigned-url:
    expiration: "2h"  # Longer expiration time
```

## Development Workflow

### Make Code Changes
```bash
# With quarkusDev running, just save files
# Changes are automatically recompiled
./gradlew :storage-service:rustfs-server:quarkusDev
```

### Run Tests
```bash
./gradlew :storage-service:rustfs-server:test
```

### Format Code
```bash
./gradlew :storage-service:rustfs-server:spotlessApply
```

### Build Production Image
```bash
cd storage-service/rustfs-server

# JVM image (fast build)
docker build -f Dockerfile.jvm -t storage-service:jvm ../..

# Native image (slow build, fast startup)
docker build -f Dockerfile.native -t storage-service:native ../..
```

## Troubleshooting

### Application Won't Start
```bash
# Check if ports are already in use
lsof -i :8080
lsof -i :9000
lsof -i :19092

# Kill processes if needed
kill -9 <PID>
```

### MinIO Connection Failed
```bash
# Verify MinIO is running
curl http://localhost:9000

# Check Docker Compose logs
docker-compose logs minio

# Restart MinIO
docker-compose restart minio
```

### Kafka Events Not Appearing
```bash
# Check Redpanda status
docker-compose ps redpanda

# Verify topic exists
docker exec -it storage-redpanda rpk topic list

# Create topic manually if needed
docker exec -it storage-redpanda rpk topic create storage.events
```

### Circuit Breaker Always Open
```bash
# Check storage backend availability
curl http://localhost:9000

# Reset by restarting application
docker-compose restart storage-service-jvm
# or Ctrl+C and restart gradlew quarkusDev
```

## API Examples

### Search Files by MIME Type
```bash
curl "http://localhost:8080/v1/files/search?mimeType=text/plain&limit=10"
```

### Update File Tags
```bash
curl -X PATCH http://localhost:8080/v1/files/{fileId}/metadata \
  -H "Content-Type: application/json" \
  -d '{"env":"production","reviewed":"true"}'
```

### Delete File
```bash
curl -X DELETE http://localhost:8080/v1/files/{fileId}
```

### Upload with Custom Metadata
```bash
curl -X POST http://localhost:8080/v1/files \
  -F "file=@document.pdf" \
  -F 'tags={"author":"john","department":"engineering","confidential":"true"}'
```

## Performance Testing

### Upload Large Files
```bash
# Create 100MB test file
dd if=/dev/zero of=large.bin bs=1M count=100

# Upload
time curl -X POST http://localhost:8080/v1/files \
  -F "file=@large.bin"
```

### Concurrent Uploads
```bash
# Install GNU parallel
# brew install parallel (macOS)

# Upload 10 files concurrently
seq 1 10 | parallel -j10 'curl -X POST http://localhost:8080/v1/files \
  -F "file=@test.txt"'
```

## Next Steps

1. Review [ARCHITECTURE.md](./ARCHITECTURE.md) for detailed system design
2. Read [REFACTORING.md](./REFACTORING.md) for architectural decisions
3. Check [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) for complete reference
4. Explore Swagger UI for all available endpoints
5. Integrate with your application using the REST API

## Getting Help

### Check Logs
```bash
# Application logs
docker-compose logs -f storage-service-jvm

# MinIO logs
docker-compose logs -f minio

# Kafka logs
docker-compose logs -f redpanda
```

### Health Checks
```bash
# Liveness (is app running?)
curl http://localhost:8080/q/health/live

# Readiness (is app ready to serve traffic?)
curl http://localhost:8080/q/health/ready

# Detailed health
curl http://localhost:8080/q/health | jq .
```

### OpenAPI Specification
```bash
# Get OpenAPI JSON
curl http://localhost:8080/q/openapi

# View in Swagger UI
open http://localhost:8080/q/swagger-ui
```

---

**Need More Details?**
- Architecture: See [ARCHITECTURE.md](./ARCHITECTURE.md)
- Configuration: See [application.yml](./src/main/resources/application.yml)
- API Reference: http://localhost:8080/q/swagger-ui
