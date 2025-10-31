# Development Guide

## Code Quality and Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) for automatic code formatting and [Checkstyle](https://checkstyle.sourceforge.io/) for code quality checks.

### Pre-commit Hooks Setup

To ensure code quality and consistent formatting, we recommend setting up pre-commit hooks that will automatically format your code before each commit.

#### Quick Setup

Run the setup script to configure Git hooks:

```bash
./scripts/setup-hooks.sh
```

#### Manual Setup

If you prefer to set up manually:

1. Configure Git to use the `.githooks` directory:
   ```bash
   git config core.hooksPath .githooks
   ```

2. Make the pre-commit hook executable:
   ```bash
   chmod +x .githooks/pre-commit
   ```

### What the Pre-commit Hook Does

The pre-commit hook will automatically:

1. **Format Java files** - Runs Spotless formatting on all staged `.java` files
2. **Apply code style** - Uses Google Java Format with import organization and unused import removal
3. **Run Checkstyle** - Validates code style compliance
4. **Stage formatted files** - Automatically adds the formatted files back to the commit

### Manual Spotless Commands

You can also run Spotless manually:

```bash
# Apply formatting to all files
./gradlew spotlessApply

# Check if files are properly formatted (without applying changes)
./gradlew spotlessCheck

# Apply formatting to specific files
./gradlew spotlessApply -PspotlessIdeHook="/path/to/file.java"
```

### Spotless Configuration

The Spotless configuration in `build.gradle` includes:

- **Google Java Format** - Standard Google code formatting
- **Import ordering** - Organizes imports in a consistent order
- **Unused import removal** - Removes unused import statements
- **CleanThat** - Additional code cleanup rules

### Troubleshooting

If you encounter issues with the pre-commit hook:

1. **Hook not running**: Ensure you've run `./scripts/setup-hooks.sh` or manually configured the hooks path
2. **Permission denied**: Make sure the hook is executable with `chmod +x .githooks/pre-commit`
3. **Formatting failures**: Run `./gradlew spotlessApply` manually to see detailed error messages

### Bypassing the Hook

In exceptional cases, you can bypass the pre-commit hook with:

```bash
git commit --no-verify -m "Your commit message"
```

**Note**: This should be used sparingly and only when absolutely necessary, as it bypasses code quality checks.
