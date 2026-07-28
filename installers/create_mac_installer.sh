#!/bin/bash

nortantis_version=$(cat version.txt)

inputFolder="installer_input"
exeName="Nortantis"

# Build the jar file
pushd ..
./gradlew :jar
popd

rm -rf "$inputFolder"
mkdir "$inputFolder"
cp "../build/libs/Nortantis.jar" "$inputFolder"

sign_args=()
if [ -n "$MACOS_SIGNING_IDENTITY" ]; then
  sign_args=(--mac-sign --mac-signing-key-user-name "$MACOS_SIGNING_IDENTITY")
fi

jpackage \
--input "$inputFolder" \
--name "$exeName" \
--main-jar "Nortantis.jar" \
--main-class nortantis.swing.MainWindow \
--type pkg \
--icon "taskbar icon medium size.icns" \
--file-associations file_associations_mac.txt \
--vendor "Joseph Heydorn" \
--app-version "$nortantis_version" \
--mac-package-name "Nortantis" \
--java-options -XX:MaxRAMPercentage=50.0 \
--java-options -XX:MaxHeapFreeRatio=40 \
--java-options -XX:MinHeapFreeRatio=20 \
--java-options -XX:G1PeriodicGCInterval=15000 \
--java-options -Dfile.encoding=UTF-8 \
--license-file end_user_license_agreement.txt \
"${sign_args[@]}"

rm -rf "$inputFolder"

