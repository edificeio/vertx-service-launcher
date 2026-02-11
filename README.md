# Vertx Service Launcher

A Vert.x-based service launcher for deploying and managing microservices with dynamic configuration, service discovery, and template-based configuration management.

## Overview

The Vertx Service Launcher is a flexible launcher that manages the lifecycle of Vert.x verticles and services. It provides configuration management, service deployment/undeployment, artifact listening, and monitoring capabilities.

## Operating Modes

### Default mode

In its default mode, the launcher starts as a full-featured service orchestrator that:

1. **Initializes the Vert.x instance** with custom configuration (metrics, worker pool size, etc.)
2. **Loads configuration** from a JSON file
3. **Manages service lifecycle**:
   - Deploys services/verticles based on configuration
   - Monitors configuration changes and redeploys services accordingly
   - Handles service undeployment and cleanup
   - Supports service restart operations
4. **Watches for artifact changes** and triggers redeployment when needed
5. **Provides service discovery** integration (Traefik, Zookeeper)
6. **Configures logging** with customizable log levels via environment variables
7. **Enables monitoring** with Micrometer metrics (Prometheus integration)
8. **Intercepts event bus messages** for distributed tracing (trace ID propagation)

The launcher listens on the event bus address `service-launcher.deployment` for deployment actions like:
- `restart-module`: Restart a specific service module
- Other deployment management commands

### Export Mode

In **export mode** (i.e. when `EXPORT_CONF_TEMPLATE` environment variable is set to `true`), the launcher operates as a utility to extract configuration templates:

1. **Exports template.j2 files** from `/srv/springboard/mods` directory structure
2. **Copies templates** to a specified export path (`EXPORT_CONF_TEMPLATE_PATH`)
3. **Exits immediately** after export is complete

This mode is useful for:
- Extracting configuration templates for documentation
- Creating configuration skeletons for new deployments
- Auditing available service configurations

To enable export mode, set the environment variable:
```bash
EXPORT_CONF_TEMPLATE=true
EXPORT_CONF_TEMPLATE_PATH=/path/to/export/directory
```

## Installation

### Using Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.opendigitaleducation</groupId>
    <artifactId>vertx-service-launcher</artifactId>
    <version>${the.version.you.want}</version>
</dependency>
```

**Note:** You need to configure the ODE repository in your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>ode</id>
        <name>ODE Repository</name>
        <url>https://maven.opendigitaleducation.com/nexus/content/groups/public</url>
    </repository>
</repositories>
```

### Using Docker

Pull the Docker image:

```bash
docker pull opendigitaleducation/vertx-service-launcher:latest
```

Or for a specific version:

```bash
docker pull opendigitaleducation/vertx-service-launcher:3.1-SNAPSHOT
```

## Building

### Prerequisites

- Java 8 (not higher)
- Maven 3.9.3
- Git

### Building the JAR

Build the fat JAR with all dependencies:

```bash
mvn clean install
```

### Building with Tests

```bash
mvn clean install
```

### Building without Tests

```bash
mvn clean install -Dmaven.test.skip=true
```

## Creating Docker Images

### Using the Build Script

The project includes a convenient build script that handles everything:

```bash
./build-image.sh
```

This script:
1. Extracts the project version from `pom.xml`
2. Builds a multi-architecture Docker image
3. Tags the image as `opendigitaleducation/vertx-service-launcher:<version>`
4. If on the `master` branch, also tags as `:latest`
5. Pushes the image to the registry

### Manual Docker Build

Build locally without pushing:

```bash
# Get the version
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
JAR_FILE="vertx-service-launcher-$VERSION-fat.jar"

# Build the image
docker build -t opendigitaleducation/vertx-service-launcher:$VERSION \
  --build-arg JAR_FILE="$JAR_FILE" \
  -f Dockerfile .
```

### Using Maven Docker Plugin

The project is configured with the Spotify Dockerfile Maven plugin:

```bash
mvn clean install dockerfile:build
```

To skip Docker builds during Maven install:

```bash
mvn clean install -DskipMavenDockerBuild=true
```

## Running

### Using Docker

#### Default mode

```bash
docker run -v /path/to/springboard:/srv/springboard \
  -e LOG_LEVEL=INFO \
  -e VERTX_LOG_LEVEL=INFO \
  opendigitaleducation/vertx-service-launcher:latest
```

#### Export Mode

```bash
docker run -v /path/to/export:/export \
  -v /path/to/springboard:/srv/springboard \
  -e EXPORT_CONF_TEMPLATE=true \
  -e EXPORT_CONF_TEMPLATE_PATH=/export \
  opendigitaleducation/vertx-service-launcher:latest
```

### Using Java

```bash
java -jar target/vertx-service-launcher-3.1-SNAPSHOT-fat.jar \
  run com.opendigitaleducation.launcher.VertxServiceLauncher \
  -conf config.json
```

## Configuration

### Environment Variables

- `LOG_LEVEL` (default: `INFO`) - General log level
- `VERTX_LOG_LEVEL` (default: `INFO`) - Vert.x framework log level
- `HAZELCAST_LOG_LEVEL` (default: `SEVERE`) - Hazelcast log level
- `NETTY_LOG_LEVEL` (default: `SEVERE`) - Netty log level
- `ACCESS_LOG_LEVEL` (default: `FINEST`) - Access log level
- `EXPORT_CONF_TEMPLATE` (default: `false`) - Enable export mode
- `EXPORT_CONF_TEMPLATE_PATH` - Path for exported templates (required in export mode)

## Features

- **Dynamic Configuration Management**: Support for JSON 
- **Service Lifecycle Management**: Deploy, undeploy, and restart services
- **Configuration Hot Reload**: Automatically redeploy services on configuration changes
- **Artifact Watching**: Monitor artifact changes and trigger redeployment
- **Service Discovery**: Integration with Traefik and Zookeeper
- **Metrics & Monitoring**: Micrometer integration with Prometheus backend
- **Distributed Tracing**: Trace ID propagation across event bus
- **Customizable Logging**: Fine-grained control over log levels
- **Multi-tenancy Support**: Folder-based service factory

## License

See the parent project for license information.

## Contributing

This project is part of the Edifice ecosystem. For contributions, please follow the organization's guidelines.
