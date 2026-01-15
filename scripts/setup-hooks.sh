#!/bin/bash

# Setup script for Git hooks
echo "Setting up Pre-commit for LAA Data Claims API..."

# Install prek globally
echo "\nInstalling prek globally"
curl --proto '=https' --tlsv1.2 \
-LsSf https://raw.githubusercontent.com/ministryofjustice/devsecops-hooks/e85ca6127808ef407bc1e8ff21efed0bbd32bb1a/prek/prek-installer.sh | sh

# Activate prek in the repository
echo "\nInstalling prek within the repository"
prek install

echo "Git hooks setup complete!"
echo "The pre-commit hook will now:"
echo "  1. Run Spotless formatting on staged Java files"
echo "  2. Run Checkstyle validation on formatted files"
echo "  3. Run Ministry of Justice - Scanner"
echo "To manually run Spotless formatting: ./gradlew spotlessApply"
echo "To check Spotless compliance: ./gradlew spotlessCheck"
