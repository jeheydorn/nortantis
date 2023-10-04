#!/bin/bash

# Before running this script, in Eclipse, change AssetsPath.isInstalled to true and then export as a runnable jar file named Nortantis.jar.
# When updating the app version, make sure to also update MapSettings.currentVersion. Although that can be skipped if MapSettings does not change.

inputFolder="installer_input"
exeName="Nortantis"

rm -rf "$inputFolder"
mkdir "$inputFolder"
cp -r "../assets" "$inputFolder/assets"
rm -f "$inputFolder/assets/books/SSA *"
cp "../Nortantis.jar" "$inputFolder"
rm -f "Nortantis-*.msi"

jpackage \
--input "$inputFolder" \
--name "$exeName" \
--main-jar "Nortantis.jar" \
--main-class nortantis.swing.MainWindow \
--type deb \
--linux-shortcut \
--icon "taskbar icon.ico" \
--file-associations file_associations.txt \
--vendor "Joseph Heydorn" \
--app-version "1.0" \
--java-options -XX:MaxRAMPercentage=50.0 \
--java-options -Dfile.encoding=UTF-8 \
--license-file end_user_license_agreement.txt

# Line to create Windows console: --win-console ^
rm -rf "$inputFolder"
# rm -f "..\Nortantis.jar"

read -p "Press enter to continue..."