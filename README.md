# Demo Application with TiDB and Monitoring Stack

A Spring Boot application with TiDB database, Prometheus monitoring, and Grafana dashboard, all orchestrated with Docker Compose.

## Architecture Overview

This application consists of the following services:

- **User Service**: Spring Boot REST API for user management
- **TiDB Cluster**: Distributed SQL database (PD, TiKV, TiDB)
- **Prometheus**: Metrics collection and monitoring
- **Grafana**: Data visualization and dashboards

## Prerequisites

- Docker and Docker Compose installed
- Java 17+ (for local development)
- Maven (for local development)

## Quick Start

### 1. Clone and Navigate to Project

```bash
git clone <repository-url>
cd demo
```

### 2. Start All Services

```bash
docker-compose up -d
```

### 3. Verify Services

```bash
docker-compose ps
```

All services should be in "Up" status.

## Service Endpoints

### 🚀 User Service (Spring Boot API)

**Base URL**: `http://localhost:8080`

#### User Management Endpoints

| Method   | Endpoint       | Description       | Request Body               |
| -------- | -------------- | ----------------- | -------------------------- |
| `POST`   | `/users`       | Create a new user | `{"name": "John Doe"}`     |
| `GET`    | `/users`       | Get all users     | -                          |
| `GET`    | `/users/{id}`  | Get user by ID    | -                          |
| `PUT`    | `/users/{id}`  | Update user       | `{"name": "Updated Name"}` |
| `DELETE` | `/users/{id}`  | Delete user by ID | -                          |
| `DELETE` | `/users/clear` | Delete all users  | -                          |

#### Bulk Operations

| Method | Endpoint             | Description                  | Parameters                                                     |
| ------ | -------------------- | ---------------------------- | -------------------------------------------------------------- |
| `POST` | `/users/bulk-insert` | Insert 10M users for testing | `batchSize` (default: 5000)<br>`threadPoolSize` (default: 200) |

**Example bulk insert:**

```bash
curl -X POST "http://localhost:8080/users/bulk-insert?batchSize=1000&threadPoolSize=50"
```

#### System Tuning Endpoints

| Method | Endpoint             | Description              | Parameters                    |
| ------ | -------------------- | ------------------------ | ----------------------------- |
| `POST` | `/system-tuning/ram` | Allocate RAM for testing | `sizeMB` (required)           |
| `POST` | `/system-tuning/cpu` | Stress CPU for testing   | `seconds` (1-60, default: 30) |

**Examples:**

```bash
# Allocate 100MB RAM
curl -X POST "http://localhost:8080/system-tuning/ram?sizeMB=100"

# Stress CPU for 15 seconds
curl -X POST "http://localhost:8080/system-tuning/cpu?seconds=15"
```

#### Health and Metrics

| Method | Endpoint               | Description               |
| ------ | ---------------------- | ------------------------- |
| `GET`  | `/actuator/health`     | Application health status |
| `GET`  | `/actuator/prometheus` | Prometheus metrics        |
| `GET`  | `/actuator/metrics`    | Application metrics       |

### 🗄️ TiDB Database Cluster

#### PD (Placement Driver)

**URL**: `http://localhost:2379`

- **Port 2379**: Client requests
- **Port 2380**: Peer communication

#### TiKV (Storage Engine)

**URL**: `http://localhost:20160`

- **Port 20160**: Client requests
- **Port 20180**: Status port

#### TiDB (SQL Layer)

**URL**: `mysql://localhost:4000`

- **Port 4000**: MySQL protocol
- **Port 10080**: Status and HTTP API

**Database Connection:**

```bash
mysql -h 127.0.0.1 -P 4000 -u root
```

### 📊 Prometheus (Metrics Collection)

**URL**: `http://localhost:9090`

#### Key Endpoints

| Endpoint        | Description             |
| --------------- | ----------------------- |
| `/`             | Prometheus web UI       |
| `/targets`      | View monitoring targets |
| `/config`       | View configuration      |
| `/api/v1/query` | Query API               |

### 📈 Grafana (Visualization)

**URL**: `http://localhost:3000`

**Default Credentials:**

- Username: `admin`
- Password: `admin`

#### Features

- Pre-configured Prometheus datasource
- Custom dashboards for application metrics
- TiDB cluster monitoring
- System performance visualization

## Usage Examples

### 1. Create a User

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Smith"}'
```

### 2. Get All Users

```bash
curl http://localhost:8080/users
```

### 3. Update a User

```bash
curl -X PUT http://localhost:8080/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Johnson"}'
```

### 4. Bulk Insert for Performance Testing

```bash
curl -X POST "http://localhost:8080/users/bulk-insert?batchSize=2000&threadPoolSize=100"
```

### 5. Monitor Application Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

## Development

### Local Development Setup

```bash
# Start only the database cluster
docker-compose up -d pd tikv tidb prometheus grafana

# Run the application locally
mvn spring-boot:run
```

### Build Application

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

## Monitoring and Observability

### View Application Metrics

1. Open Grafana: `http://localhost:3000`
2. Login with admin/admin
3. Import dashboards or create custom ones
4. Monitor application performance, database metrics, and system resources

### Prometheus Metrics

- Application metrics: `http://localhost:9090`
- Query examples:
  - JVM memory: `jvm_memory_used_bytes`
  - HTTP requests: `http_server_requests_seconds_count`
  - Custom business metrics from the application

## Troubleshooting

### Check Service Status

```bash
docker-compose ps
docker-compose logs [service-name]
```

### Common Issues

1. **Port conflicts**: Ensure ports 3000, 4000, 8080, 9090 are not in use
2. **Database connection**: Wait for TiDB cluster to be fully initialized
3. **Memory issues**: Ensure sufficient Docker memory allocation (recommended: 4GB+)

### Restart Services

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart user-service
```

### Clean Up

```bash
# Stop all services
docker-compose down

# Remove volumes (data will be lost)
docker-compose down -v
```

## Performance Testing

The application includes built-in performance testing capabilities:

1. **Bulk Insert**: Test database write performance with millions of records
2. **RAM Allocation**: Test memory usage and garbage collection
3. **CPU Stress**: Test CPU performance under load

Monitor these tests through Grafana dashboards to observe system behavior under stress.

## Configuration

### Environment Variables

- Database configuration in `application.yml`
- Prometheus configuration in `prometheus/prometheus.yml`
- Grafana provisioning in `grafana/provisioning/`
