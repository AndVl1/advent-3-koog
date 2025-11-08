# CI/CD Workflows

This directory contains GitHub Actions workflows for automated testing and building.

## Workflows

### 1. Build and Test (`build.yml`)

**Triggers:**
- Pull requests to `main`, `master`, or `develop` branches
- Pushes to `main`, `master`, or `develop` branches

**Parallel Jobs:**

#### Desktop GUI (`build-desktop-app`)
- **Purpose**: Build Compose Desktop application
- **Runtime**: Ubuntu, ~15 minutes
- **Tasks**:
  - Checkout code
  - Setup JDK 17
  - Build desktop-app module
  - Upload build logs on failure

#### CLI (`build-cli`)
- **Purpose**: Build command-line interface
- **Runtime**: Ubuntu, ~10 minutes
- **Tasks**:
  - Checkout code
  - Setup JDK 17
  - Build CLI module
  - Install distribution
  - Test `--help` command
  - Upload build logs on failure

#### Backend Server (`build-backend`)
- **Purpose**: Build Ktor backend server
- **Runtime**: Ubuntu, ~15 minutes
- **Tasks**:
  - Checkout code
  - Setup JDK 17
  - Build server module
  - Create Shadow JAR (~43MB)
  - Verify JAR size
  - Upload build logs on failure

#### Build Summary (`build-summary`)
- **Purpose**: Aggregate results from all builds
- **Runs**: After all builds complete
- **Behavior**: Fails if any build failed

### 2. MCP Servers Build (`mcp-servers.yml`)

**Triggers:**
- Pull requests to `main`, `master`, or `develop` branches (when MCP files change)
- Pushes to `main`, `master`, or `develop` branches (when MCP files change)

**Matrix Strategy:**
Builds 4 MCP servers in parallel:
- `github` - GitHub integration MCP server
- `googledocs` - Google Docs/Sheets MCP server
- `telegraph` - Telegraph MCP server
- `test` - Test MCP server

**Each MCP Job:**
- **Runtime**: Ubuntu, ~10 minutes
- **Tasks**:
  - Checkout code
  - Setup JDK 17
  - Build Shadow JAR: `./gradlew :mcp:{server}:shadowJar`
  - Verify JAR exists (auto-detects JAR name with wildcard)
  - Check JAR size
  - Upload build logs on failure

**Note:** JAR naming varies by module:
- `github` → `github-0.1.0.jar`
- `googledocs` → `googledocs-0.1.0.jar`
- `telegraph` → `telegraph-0.1.0.jar`
- `test` → `mcp-test-0.1.0.jar` (different basename)

## Optimizations

### Gradle Caching
All workflows use `actions/setup-java@v4` with `cache: 'gradle'` to cache:
- Dependencies
- Gradle wrapper
- Build cache

This reduces build time by ~50% on subsequent runs.

### Concurrency Control
Both workflows use concurrency groups to cancel in-progress runs when new commits are pushed:
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

### Fail-Fast Strategy
- Main build workflow: Jobs are independent; failure in one doesn't stop others
- MCP workflow: `fail-fast: false` - all MCP servers build even if one fails

## Local Testing

Test the builds locally before pushing:

```bash
# Desktop App
./gradlew :desktop-app:build -x test

# CLI
./gradlew :cli:build -x test
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli --help

# Backend
./gradlew :server:build -x test
./gradlew :server:shadowJar

# MCP Servers
./gradlew :mcp:github:shadowJar
./gradlew :mcp:googledocs:shadowJar
./gradlew :mcp:telegraph:shadowJar
./gradlew :mcp:test:shadowJar
```

## Build Artifacts

When builds fail, artifacts are uploaded with 7-day retention:
- `desktop-app-build-logs`
- `cli-build-logs`
- `server-build-logs`
- `mcp-{server}-build-logs`

Artifacts include:
- Build reports (`build/reports/`)
- Test results (`build/test-results/`)

## Environment Variables

No environment variables are required for CI builds. The workflows build without:
- API keys (not needed for compilation)
- `.env` files (server loads them via `dotenv`, but builds without them)

## Timeouts

All jobs have timeouts to prevent hanging builds:
- Desktop App: 15 minutes
- CLI: 10 minutes
- Backend: 15 minutes
- MCP Servers: 10 minutes each

## Status Badges

Add these to your main README.md:

```markdown
[![Build Status](https://github.com/AndVl1/chatter/actions/workflows/build.yml/badge.svg)](https://github.com/AndVl1/chatter/actions/workflows/build.yml)
[![MCP Servers](https://github.com/AndVl1/chatter/actions/workflows/mcp-servers.yml/badge.svg)](https://github.com/AndVl1/chatter/actions/workflows/mcp-servers.yml)
```

## Troubleshooting

### Build Fails on CI but Works Locally

1. **Check JDK version**: CI uses JDK 17, ensure local environment matches
2. **Clean build**: CI always builds from scratch
   ```bash
   ./gradlew clean build
   ```
3. **Check for local dependencies**: Ensure no local-only paths in build files

### Gradle Daemon Issues

CI uses `--no-daemon` flag to avoid daemon-related issues. For consistency:
```bash
./gradlew --no-daemon build
```

### Shadow JAR Not Found

Ensure the module has Shadow plugin configured:
```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "x.x.x"
}
```

## Future Improvements

- [ ] Add test execution (currently using `-x test`)
- [ ] Add code coverage reporting (JaCoCo)
- [ ] Add static analysis (detekt, ktlint)
- [ ] Add dependency vulnerability scanning
- [ ] Add Docker image builds for backend
- [ ] Add release workflows for publishing artifacts
