#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="${ROOT}/android"
PROPS="${ANDROID}/keystore.properties"
OUT="${ROOT}/release"

export JAVA_HOME="${JAVA_HOME:-/home/lucas/.local/jdk/jdk-17}"

if [[ ! -f "${PROPS}" ]]; then
  echo "Missing ${PROPS}"
  echo "Run: ./scripts/generate-release-keystore.sh"
  exit 1
fi

cd "${ANDROID}"
./gradlew bundleRelease

AAB="${ANDROID}/app/build/outputs/bundle/release/app-release.aab"
mkdir -p "${OUT}"
cp "${AAB}" "${OUT}/signal-scoop-play.aab"
ls -lh "${OUT}/signal-scoop-play.aab"
echo ""
echo "Upload release/signal-scoop-play.aab in Google Play Console → Release → Production."
