#!/bin/bash

# Setup script for Git hooks
echo "Setting up Git hooks for LAA Data Claims API..."

# Configure Git to use the .githooks directory
git config core.hooksPath .githooks

# Make sure the pre-commit hook is executable
chmod +x .githooks/pre-commit

echo "Git hooks setup complete!"
echo "The pre-commit hook will now:"
echo "  1. Run Spotless formatting on staged Java files"
echo "  2. Run Checkstyle validation on formatted files"
echo "  3. Automatically stage the formatted files"
echo ""
echo "To manually run Spotless formatting: ./gradlew spotlessApply"
echo "To check Spotless compliance: ./gradlew spotlessCheck"
