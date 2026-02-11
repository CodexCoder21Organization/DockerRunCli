# DockerRunCli

Command-line interface for the `url://dockerrun/` service. Allows starting, pausing, unpausing, and terminating Docker containers remotely.

## Overview

DockerRunCli connects to the [DockerRunServerService](https://github.com/CodexCoder21Organization/DockerRunServerService) via the URL Protocol (`url://dockerrun/`) using P2P networking with SJVM sandboxed execution. It provides a simple CLI for managing Docker containers on a remote server.

## Building

```bash
# Build the fat JAR
scripts/build.bash dockerruncli.buildFatJar

# Run with --launch flag
scripts/build.bash --launch dockerruncli.buildFatJar -- help
```

### Running the built JAR

```bash
java -jar docker-run-cli.jar <command> [options]
```

## Commands

| Command | Description |
|---------|-------------|
| `health` | Check if the docker run service is reachable |
| `start <image> [--env KEY=VAL]... [--timeout SECS]` | Start a new container |
| `list` | List all containers |
| `show <uuid>` | Show container details |
| `pause <uuid>` | Pause a running container |
| `unpause <uuid>` | Unpause a paused container |
| `terminate <uuid>` | Terminate a container |

## Examples

```bash
# Check service health
java -jar docker-run-cli.jar health

# Start an nginx container with env vars and 1-hour auto-termination
java -jar docker-run-cli.jar start docker.io/library/nginx:latest \
  --env PORT=8080 --env MODE=production --timeout 3600

# List all containers
java -jar docker-run-cli.jar list

# Show container details
java -jar docker-run-cli.jar show 550e8400-e29b-41d4-a716-446655440000

# Pause / Unpause
java -jar docker-run-cli.jar pause 550e8400-e29b-41d4-a716-446655440000
java -jar docker-run-cli.jar unpause 550e8400-e29b-41d4-a716-446655440000

# Terminate
java -jar docker-run-cli.jar terminate 550e8400-e29b-41d4-a716-446655440000

# JSON output for scripting
java -jar docker-run-cli.jar list --json
```

## Maven Coordinates

```
dockerruncli:docker-run-cli:0.0.1
```
