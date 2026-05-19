#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="${ROOT}/release/signalsoop-upload.jks"
mkdir -p "${ROOT}/release"

if [[ -f "${KEYSTORE}" ]]; then
  echo "Keystore already exists: ${KEYSTORE}"
  echo "Delete it first if you need a new one (you cannot recover a lost keystore)."
  exit 1
fi

echo "Creating upload keystore for Google Play."
echo "Use a strong password and store it in a password manager."
echo "If you lose this keystore, you cannot update the app on Play Store."
echo ""

read -r -p "Keystore password: " -s STORE_PASS
echo ""
read -r -p "Key password (Enter to match keystore): " -s KEY_PASS
echo ""
KEY_PASS="${KEY_PASS:-$STORE_PASS}"

keytool -genkeypair \
  -v \
  -keystore "${KEYSTORE}" \
  -alias signalsoop \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "${STORE_PASS}" \
  -keypass "${KEY_PASS}" \
  -dname "CN=Signal Scoop, OU=Mobile, O=Signal Scoop, L=Unknown, ST=Unknown, C=US"

PROPS="${ROOT}/android/keystore.properties"
cat > "${PROPS}" <<EOF
storeFile=../release/signalsoop-upload.jks
storePassword=${STORE_PASS}
keyAlias=signalsoop
keyPassword=${KEY_PASS}
EOF
chmod 600 "${PROPS}"

echo ""
echo "Created:"
echo "  ${KEYSTORE}"
echo "  ${PROPS}"
echo ""
echo "Next: ./scripts/build-play-bundle.sh"
