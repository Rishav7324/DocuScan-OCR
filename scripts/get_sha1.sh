#!/usr/bin/env bash
# Print SHA-1 fingerprints for the DocuScan OCR signing configs.
# Run from the project root:  bash scripts/get_sha1.sh
# For the release keystore, set STORE_PASSWORD (and KEY_ALIAS if not "upload"):
#   STORE_PASSWORD='...' bash scripts/get_sha1.sh
set -e

echo "=== Debug keystore SHA-1 ==="
if [ -f "$HOME/.android/debug.keystore" ]; then
  keytool -list -v \
    -keystore "$HOME/.android/debug.keystore" \
    -alias androiddebugkey \
    -storepass android -keypass android 2>/dev/null | grep -i "SHA1:"
else
  echo "No default debug keystore found at ~/.android/debug.keystore"
  echo "It is created automatically on first 'gradle assembleDebug' build."
fi

echo
echo "=== Release keystore (keystore.p12) SHA-1 ==="
if [ -f "keystore.p12" ]; then
  if [ -z "$STORE_PASSWORD" ]; then
    echo "STORE_PASSWORD not set. Re-run with:  STORE_PASSWORD='...' bash scripts/get_sha1.sh"
  else
    keytool -list -v \
      -keystore "keystore.p12" -storetype PKCS12 \
      -storepass "$STORE_PASSWORD" \
      -alias "${KEY_ALIAS:-upload}" 2>/dev/null | grep -i "SHA1:" \
      || echo "Failed (wrong password or alias '${KEY_ALIAS:-upload}'). Check KEY_ALIAS / STORE_PASSWORD."
  fi
else
  echo "No keystore.p12 found in project root."
fi

echo
echo "=== Gradle signingReport (all configs) ==="
if command -v ./gradlew >/dev/null 2>&1; then
  ./gradlew signingReport
else
  echo "gradlew not found. Run 'gradle signingReport' from the project root instead."
fi
