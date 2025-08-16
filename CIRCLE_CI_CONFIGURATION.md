# CircleCI Configuration Guide

## Configurable Branch Patterns

The CircleCI configuration supports configurable branch patterns for different actions, allowing you to customize which branches trigger Docker packaging and coverage publishing.

### Default Configuration

By default, the following actions are restricted to specific branches:

- **Docker Packaging**: `master` branch only
- **Coverage Publishing**: `master` and `codacy.*` branches only

### Configurable Parameters

The following parameters can be customized:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `docker-branch-pattern` | `master` | Branch pattern for Docker image packaging |
| `coverage-branch-pattern` | `master` | Branch pattern for coverage publishing |
| `codacy-branch-pattern` | `/^codacy.*/` | Branch pattern for Codacy coverage reports |

### How to Configure

#### Option 1: CircleCI Environment Variables (Recommended)

Set these environment variables in your CircleCI project settings:

1. Go to your CircleCI project settings
2. Navigate to "Environment Variables"
3. Add the following variables:

```bash
DOCKER_BRANCH_PATTERN=master,mike/**
COVERAGE_BRANCH_PATTERN=master,mike/**
CODACY_BRANCH_PATTERN=/^codacy.*/
```

#### Option 2: Modify the Config File

You can directly modify the `.circleci/config.yml` file to change the branch patterns:

```yaml
# In the workflow section, change the branch filters:
- package-shell:
    filters:
      branches:
        only: master,mike/**
```

#### Option 3: Pipeline API (Advanced)

You can trigger pipelines with custom configurations via the API, but this requires more complex setup.

### Branch Pattern Examples

#### Single Branch
```yaml
docker-branch-pattern: "main"
```

#### Multiple Specific Branches
```yaml
docker-branch-pattern: "main,develop,release/*"
```

#### Regex Pattern
```yaml
docker-branch-pattern: "/^release-.*/"
```

#### All Branches (Not Recommended for Production)
```yaml
docker-branch-pattern: ".*"
```

### Use Cases

#### Development Workflow
- Set `docker-branch-pattern` to `develop` for development builds
- Set `coverage-branch-pattern` to `develop` for development coverage

#### Feature Branch Workflow
- Set `docker-branch-pattern` to `feature/*` for feature branch testing
- Set `coverage-branch-pattern` to `feature/*` for feature branch coverage

#### Release Workflow
- Set `docker-branch-pattern` to `release/*` for release candidate builds
- Set `coverage-branch-pattern` to `release/*` for release coverage

### Security Considerations

⚠️ **Important**: Be careful when configuring branch patterns for Docker packaging:

1. **Docker Images**: Only package Docker images on branches you trust
2. **Registry Access**: Ensure only authorized branches can push to your container registry
3. **Resource Usage**: Docker builds consume significant resources and time

### Current Configuration

The current configuration uses these defaults:

```yaml
# In .circleci/config.yml workflow section:
- package-shell:
    filters:
      branches:
        only: master,mike/**
- publish-coverage:
    filters:
      branches:
        only:
          - master
          - mike/**
          - /^codacy.*/
```

This means:
- Docker images are built and published from the `master` branch and any branch matching `mike/**`
- Coverage reports are published from `master`, `mike/**` branches, and any branch starting with `codacy`
- All other branches will only run the build and test steps

### Migration from Master to Main

If you're migrating from `master` to `main`:

1. Update the default parameters in `.circleci/config.yml`:
   ```yaml
   docker-branch-pattern:
     type: string
     default: "main"
   coverage-branch-pattern:
     type: string
     default: "main"
   ```

2. Or set environment variables in CircleCI:
   ```bash
   DOCKER_BRANCH_PATTERN=main
   COVERAGE_BRANCH_PATTERN=main
   ```
