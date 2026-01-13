# Development Guide

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
