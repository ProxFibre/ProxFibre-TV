#!/bin/bash
# Compile ProxFibre.apk sans Android Studio (Ubuntu/Debian/WSL) :
#   sudo apt install openjdk-17-jdk aapt zipalign apksigner dalvik-exchange
#   bash build.sh
# L'APK embarque Armadillo (assets/armadillo) et AirPlay Server (assets/airplay), d'ou sa taille (~43 Mo).
set -e
cd "$(dirname "$0")"
# android.jar du SDK ; sur Debian/Ubuntu : android-sdk-platform-31, sinon ANDROID_JAR=...
SDK="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-31/android.jar}"
[ -f "$SDK" ] || { echo "android.jar introuvable : installez android-sdk-platform-31 ou definissez ANDROID_JAR"; exit 1; }
rm -rf out && mkdir -p out/classes
javac --release 8 -nowarn -cp "$SDK" -d out/classes $(find src -name '*.java')
dalvik-exchange --dex --output=out/classes.dex out/classes
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$SDK" -F out/unsigned.apk
(cd out && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 out/unsigned.apk out/aligned.apk
[ -f key.jks ] || keytool -genkeypair -keystore key.jks -storepass changeit -keypass changeit -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ProxFibre" >/dev/null
apksigner sign --ks key.jks --ks-pass pass:changeit --out out/ProxFibre.apk out/aligned.apk
echo "OK : out/ProxFibre.apk"
