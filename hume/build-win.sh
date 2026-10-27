#!/bin/bash
# Builds and signs build/tvos-home.apk on Windows (Git Bash), with the same steps as build.sh.
#
# Armadillo and the AirPlay server ship inside the APK: aapt2 packages assets/armadillo and
# assets/airplay as-is, and Bundled.java extracts and offers them on first run. The two APKs are
# kept once for the whole project, in ecran-accueil/source/assets, and copied here when missing.
set -euo pipefail
cd "$(dirname "$0")"

SHARED="$(cd ../ecran-accueil/source/assets && pwd)"
for app in armadillo airplay; do
    if [ ! -f "assets/$app/$app.apk" ]; then
        mkdir -p "assets/$app"
        cp "$SHARED/$app/"* "assets/$app/"
    fi
done

# JDK 17+ and the Android SDK. Override with JAVA_HOME / ANDROID_HOME.
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}}"
SDK_W="$(cygpath -m "$SDK" 2>/dev/null || echo "$SDK")"
BT="$SDK_W/build-tools/34.0.0"
D8="$SDK_W/build-tools/35.0.0/d8.bat"
JAR="$SDK_W/platforms/android-34/android.jar"
VERSION_CODE="${VERSION_CODE:-10003}"   # 1.0.3; kept above the old 12 so it installs over it
VERSION_NAME="${VERSION_NAME:-1.0.3}"

for f in "$BT/aapt2.exe" "$D8" "$JAR"; do
    [ -e "$f" ] || { echo "Missing $f — install build-tools 34/35 and platform android-34." >&2; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found — install JDK 17+ or set JAVA_HOME." >&2; exit 1; }

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml \
    -A assets --java build/gen --min-sdk-version 24 --target-sdk-version 34 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

# aapt2 on Windows names nested asset entries with backslashes (assets/fonts\x.ttf), which
# AssetManager cannot open on the device. Rewrite those names with forward slashes.
python - <<'PY'
import zipfile, shutil
src, tmp = 'build/unsigned.apk', 'build/fixed.apk'
zin, zout = zipfile.ZipFile(src), zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED)
for it in zin.infolist():
    zi = zipfile.ZipInfo(it.filename.replace('\\', '/'), date_time=it.date_time)
    zi.compress_type = it.compress_type
    zi.external_attr = it.external_attr
    zout.writestr(zi, zin.read(it.filename))
zin.close(); zout.close(); shutil.move(tmp, src)
PY

javac -encoding UTF-8 --release 11 -classpath "$JAR" \
    -d build/classes $(find src build/gen -name '*.java')
(cd build/classes && jar cf ../classes.jar .)
"$D8" --release --min-api 24 --lib "$JAR" --output build/dex build/classes.jar
(cd build/dex && jar uf ../unsigned.apk classes.dex)

"$BT/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk

# Signing. Release builds: RELEASE_KEYSTORE + RELEASE_KEYSTORE_PASS (the private key lives in the
# separate, private hume-launcher-signing repo). Development builds: a local debug key.
if [ -n "${RELEASE_KEYSTORE:-}" ]; then
    [ -n "${RELEASE_KEYSTORE_PASS:-}" ] || { echo "Set RELEASE_KEYSTORE_PASS for $RELEASE_KEYSTORE" >&2; exit 1; }
    KS_W="$(cygpath -m "$RELEASE_KEYSTORE" 2>/dev/null || echo "$RELEASE_KEYSTORE")"
    "$BT/apksigner.bat" sign --ks "$KS_W" --ks-pass env:RELEASE_KEYSTORE_PASS \
        --ks-key-alias "${RELEASE_KEY_ALIAS:-hume-release}" --out build/ProxFibre.apk build/aligned.apk
    echo "Signed with the release key"
    echo "Built hume/build/ProxFibre.apk ($VERSION_NAME, versionCode $VERSION_CODE)"
    exit 0
fi

KS=proxfibre.jks
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass changeit -keypass changeit -alias k \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ProxFibre debug" >/dev/null
fi
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:changeit --key-pass pass:changeit \
    --out build/ProxFibre.apk build/aligned.apk
echo "Signed with the local debug key (development build)"
echo "Built hume/build/ProxFibre.apk ($VERSION_NAME, versionCode $VERSION_CODE)"
