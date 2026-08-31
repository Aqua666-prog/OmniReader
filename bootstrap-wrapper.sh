#!/usr/bin/env sh
set -eu

VERSION="9.5.0"
EXPECTED="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEST="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"
URL="https://raw.githubusercontent.com/gradle/gradle/v${VERSION}/gradle/wrapper/gradle-wrapper.jar"

mkdir -p "$(dirname "$DEST")"

if [ -f "$DEST" ]; then
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$DEST" | awk '{print $1}')
    if [ "$ACTUAL" = "$EXPECTED" ]; then
      echo "Gradle Wrapper JAR ${VERSION} is already present and verified."
      exit 0
    fi
    echo "Existing wrapper JAR has a wrong checksum; replacing it." >&2
  else
    echo "Wrapper JAR already exists; sha256sum is unavailable, leaving it unchanged."
    exit 0
  fi
fi

TMP="${DEST}.tmp"
rm -f "$TMP"
if command -v curl >/dev/null 2>&1; then
  curl --fail --location --retry 3 --output "$TMP" "$URL"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$TMP" "$URL"
else
  echo "curl or wget is required to bootstrap the Gradle Wrapper JAR." >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$TMP" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$TMP" | awk '{print $1}')
else
  echo "No SHA-256 utility found; refusing to install an unverified wrapper JAR." >&2
  rm -f "$TMP"
  exit 1
fi

if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Gradle Wrapper checksum mismatch." >&2
  echo "Expected: $EXPECTED" >&2
  echo "Actual:   $ACTUAL" >&2
  rm -f "$TMP"
  exit 1
fi

mv "$TMP" "$DEST"
echo "Installed and verified Gradle Wrapper JAR ${VERSION}."
