#!/usr/bin/env bash
# Build PixelPress Tycoon into a signed APK without the Google SDK:
# aapt (resource link) -> javac (against android-34.jar) -> dx (dex) -> aapt add -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"

SDK=sdk/android-34.jar
SRC=app/src/main
OUT=build
KEYSTORE=release.keystore
STOREPASS=pixelpress

if [ ! -f "$SDK" ]; then
  echo "== fetching android-34.jar =="
  mkdir -p sdk
  curl -fsSL -o "$SDK" \
    "https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"

python3 tools/make_icon.py "$SRC/res/drawable/ic_launcher.png"

echo "== aapt: generate R.java =="
aapt package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$SDK"

echo "== javac =="
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$SDK" \
      -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "^warning:" || true
test -d "$OUT/classes/com/pixelpress/tycoon"

echo "== dx: dex =="
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "== aapt: package apk =="
aapt package -f -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$SDK" -F "$OUT/app.unsigned.apk"
(cd "$OUT" && aapt add app.unsigned.apk classes.dex)

# targetSdk>=30 requires resources.arsc stored uncompressed & 4-byte aligned
python3 tools/fix_arsc.py "$OUT/app.unsigned.apk"

echo "== zipalign =="
zipalign -f 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

echo "== sign =="
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias pixelpress -keyalg RSA -keysize 2048 \
          -validity 10000 -storepass "$STOREPASS" -keypass "$STOREPASS" \
          -dname "CN=PixelPress Tycoon, O=PixelPress" 2>/dev/null
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias pixelpress \
          --ks-pass "pass:$STOREPASS" --key-pass "pass:$STOREPASS" \
          --out "$OUT/PixelPressTycoon.apk" "$OUT/app.aligned.apk"
apksigner verify --min-sdk-version 21 "$OUT/PixelPressTycoon.apk"

ls -la "$OUT/PixelPressTycoon.apk"
echo "OK: $OUT/PixelPressTycoon.apk"
