#!/bin/bash

# Before running this script, in Eclipse, change AssetsPath.isInstalled to true and then export as a runnable jar file named Nortantis.jar.
# When updating the app version, make sure to also update MapSettings.currentVersion.

inputFolder="installer_input"
exeName="Nortantis"

# Build the jar file
pushd
gradle :jar
popd

rm -rf "$inputFolder"
mkdir "$inputFolder"
cp -r "../assets" "$inputFolder/assets"
cp "../build/libs/Nortantis.jar" "$inputFolder"

jpackage \
--input "$inputFolder" \
--name "$exeName" \
--main-jar "Nortantis.jar" \
--main-class nortantis.swing.MainWindow \
--type deb \
--linux-shortcut \
--icon "taskbar icon.png" \
--file-associations file_associations.txt \
--vendor "Joseph Heydorn" \
<<<<<<< HEAD
--app-version "2.85" \
=======
--app-version "2.9" \
>>>>>>> 3bf0c91 (Working on UI for more region boundary options.)
--java-options -XX:MaxRAMPercentage=50.0 \
--java-options -Dfile.encoding=UTF-8 \
--license-file end_user_license_agreement.txt

# Line to create Windows console: --win-console ^
rm -rf "$inputFolder"
# rm -f "..\Nortantis.jar"

