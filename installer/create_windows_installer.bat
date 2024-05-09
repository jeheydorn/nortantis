REM Before running this script, in Eclipse, change AssetsPath.isInstalled to true and then export as a runnable jar file named Nortantis.jar.

REM When updating the app version, make sure to also update MapSettings.currentVersion. Although that can be skipped if MapSettings does not change.

set inputFolder=installer_input
set exeName=Nortantis

RMDIR /S /Q installer_input
MKDIR %inputFolder%
Xcopy "../assets" "%inputFolder%/assets" /E /I
copy ..\Nortantis.jar %inputFolder%
DEL "Nortantis Fantasy Maps-*.msi"

jpackage ^
--input "%inputFolder%" ^
--name "%exeName%" ^
--main-jar "Nortantis.jar" ^
--main-class nortantis.swing.MainWindow ^
--type msi ^
--win-menu ^
--win-shortcut ^
--icon "taskbar icon.ico" ^
--file-associations file_associations.txt ^
--vendor "Joseph Heydorn" ^
--app-version "2.4" ^
--java-options -XX:MaxRAMPercentage=50.0 ^
--license-file end_user_license_agreement.txt ^
--java-options -Dfile.encoding=UTF-8


REM Line to create Windows console: --win-console ^

REM line to add license file: --license-file end_user_license_agreement.txt ^

RMDIR /S /Q installer_input
REM DEL "..\Nortantis.jar"

pause