REM Before running this script, in Eclipse, change AssetsPath.isInstalled to true and then export as a runnable jar file named Nortantis.jar.

REM When updating the app version, make sure to also update MapSettings.currentVersion. Although that can be skipped if MapSettings does not change.

set inputFolder=installer_input
set exeName=Nortantis

RMDIR /S /Q installer_input
MKDIR %inputFolder%
Xcopy "../assets" "%inputFolder%/assets" /E /I
DEL "%inputFolder%\assets\books\SSA *"
copy ..\Nortantis.jar %inputFolder%
DEL "Nortantis-*.msi"

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
--app-version "1.0" ^
--java-options -XX:MaxRAMPercentage=50.0 ^
--java-options -Dfile.encoding=UTF-8 ^
--license-file end_user_license_agreement.txt

REM Line to create Windows console: --win-console ^

RMDIR /S /Q installer_input
REM DEL "..\Nortantis.jar"

pause