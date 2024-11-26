#!/bin/bash

# When updating the app version, make sure to also update MapSettings.currentVersion.
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
cp "../assets/internal/taskbar icon.png" "$inputFolder"

jpackage \
--input "$inputFolder" \
--name "$exeName" \
--main-jar "Nortantis.jar" \
--main-class nortantis.swing.MainWindow \
--type deb \
--linux-shortcut \
--icon "taskbar icon.png" \
--file-associations file_associations_linux.txt \
--vendor "Joseph Heydorn" \
--app-version "$nortantis_version" \
--java-options -XX:MaxRAMPercentage=50.0 \
--java-options -Dfile.encoding=UTF-8 \
--license-file end_user_license_agreement.txt

rm -rf "$inputFolder"
# rm -f "..\Nortantis.jar"

