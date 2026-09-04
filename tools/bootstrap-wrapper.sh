#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Install Gradle 9.5.0 or use the GitHub Actions workflow." >&2
  exit 1
fi
gradle wrapper --gradle-version 9.5.0 --distribution-type bin
chmod +x ./gradlew
echo "Gradle wrapper created."
