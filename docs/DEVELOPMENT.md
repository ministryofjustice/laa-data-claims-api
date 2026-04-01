# Development Guide

## UAT Preview releases

### Introduction

In this project we do not have a Dev environment, instead we use "ephemeral" deployments in the UAT environment with their own Postgres DB.

### Steps involved

1. Local development can be done against a Docker based Postgres DB.
2. Once the branch is locally tested (including any Unit tests and Integration tests added and passing) and a PR is created, the github actions get triggered and do the following:
   1. The `deploy-uat-preview` workflow (`.github/workflows/deploy-uat-preview.yml`) is triggered by the PR creation event.
   2. It builds and tests the code, publishes a Docker image to ECR, and then calls the `deploy_branch` action (`.github/actions/deploy_branch/action.yml`).
   3. The `deploy_branch` action first calls the `get_release_name` action (`.github/actions/get_release_name/action.yml`), which derives a **release name** from the PR number in the format `pr-<number>` (e.g. `pr-101`).
   4. It then authenticates to the Kubernetes cluster and logs in to ECR.
   5. Using the release name as a database name, it creates a **dedicated Postgres database** named `pr-<number>` in the shared UAT RDS instance by running `scripts/uat_create_db.sh`. This gives the preview deployment its own isolated database.
   6. It then performs a **Helm deployment** into the UAT Kubernetes namespace, using the release name as the Helm release name. This creates a fully independent deployment of the application with its own ingress URLs in the format:
      - External: `pr-<number>-<namespace>.cloud-platform.service.justice.gov.uk`
      - Internal: `pr-<number>-internal-<namespace>.internal-non-prod.cloud-platform.service.justice.gov.uk`
   7. The app is deployed with `spring.profile=preview` and `database.name=pr-<number>`, pointing it at the newly created database. Flyway will run migrations against this fresh database on startup.
      - NOTE: Some flyway migrations have a special "preview" versions which are kept in the folder `db/migration-preview`
   8. After a successful deployment, the release is also recorded in the Pact Broker under the release name.

3. When the PR is **merged or closed**, the `delete-preview-release` workflow (`.github/workflows/delete-preview-release.yml`) is triggered and does the following:
   1. It calls the `cleanup_branch` action (`.github/actions/cleanup_branch/action.yml`), which again uses `get_release_name` to reconstruct the same `pr-<number>` release name from the PR number.
   2. It runs `helm uninstall` to **remove the Helm release** and all its associated Kubernetes resources (pods, services, ingresses, etc.) from the UAT namespace.
   3. It runs `scripts/uat_drop_db.sh` to **permanently delete the preview database** (`pr-<number>`) from the shared UAT RDS instance. Protected databases (e.g. `main`, `postgres`) cannot be dropped by this script.
   4. Finally, it deletes any leftover **Helm release history secrets** from the namespace.

## Code Quality and Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) for automatic code formatting, [Checkstyle](https://checkstyle.sourceforge.io/) for code quality checks, and [Ministry of Justice DevSecOps Hooks](https://github.com/ministryofjustice/devsecops-hooks) for security scanning.

### Pre-commit Hooks Setup

The project is configured with pre-commit hooks that run automatically on each commit to ensure code quality and security.

#### Setup

Run the setup script to configure Git hooks:
```bash
./scripts/setup-hooks.sh
```

### What the Pre-commit Hooks Do

The pre-commit hooks will automatically:

1. **Format Java files** - Runs Spotless formatting on all staged `.java` files
2. **Run Checkstyle** - Validates code style compliance
3. **Security Scanning** - Runs MoJ security scanner to detect secrets and vulnerabilities

### Manual Commands

You can also run the tools manually:

```bash
# Format all files
./gradlew spotlessApply

# Check formatting without applying changes
./gradlew spotlessCheck

# Run Checkstyle
./gradlew checkstyleStaged

# Run all checks manually
pre-commit run --all-files
```

### Configuration

The pre-commit configuration (`.pre-commit-config.yaml`) includes:

- **Spotless** - For code formatting
- **Checkstyle** - For code quality checks
- **MoJ DevSecOps Hooks** - For security scanning

### Troubleshooting

If you encounter issues:

1. **Permission issues**:
   ```bash
   chmod +x .git/hooks/pre-commit
   ```

2. **Bypass hooks** (use sparingly):
   ```bash
   git commit --no-verify -m "Your message"
   ```

### Requirements

- prek (MoJ DevSecOps Hooks)
- Java Development Kit (JDK)
- Gradle

For more information, see:
- [pre-commit documentation](https://pre-commit.com/)
- [MoJ DevSecOps Hooks](https://github.com/ministryofjustice/devsecops-hooks)
