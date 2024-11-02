REM When updating the app version, make sure to also update MapSettings.currentVersion.

set inputFolder=installer_input
set exeName=Nortantis

REM Build the jar file
pushd ..
call gradle :jar
popd

RMDIR /S /Q installer_input
MKDIR %inputFolder%
copy ..\build\libs\Nortantis.jar %inputFolder%

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
--app-version "2.9" ^
--java-options -XX:MaxRAMPercentage=50.0 ^
--java-options -Dsun.java2d.d3d=false ^
--license-file end_user_license_agreement.txt ^
--win-console ^
--java-options -Dfile.encoding=UTF-8


REM Line to create Windows console: --win-console ^

REM line to add license file: --license-file end_user_license_agreement.txt ^

RMDIR /S /Q installer_input
REM DEL "..\Nortantis.jar"

pause