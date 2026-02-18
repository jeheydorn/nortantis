
REM When updating the app version, make sure to also update MapSettings.currentVersion.
set /p nortantis_version=< version.txt

set inputFolder=installer_input
set exeName=Nortantis

REM Build the jar file
pushd ..
call .\gradlew :jar
popd

IF EXIST installer_input (
    RMDIR /S /Q installer_input
)
MKDIR %inputFolder%
copy ..\build\libs\Nortantis.jar %inputFolder%
copy "taskbar icon.ico" %inputFolder%

jpackage ^
--input "%inputFolder%" ^
--name "%exeName%" ^
--main-jar "Nortantis.jar" ^
--main-class nortantis.swing.MainWindow ^
--type msi ^
--win-menu ^
--win-shortcut ^
--icon "taskbar icon.ico" ^
--file-associations file_associations_windows.txt ^
--vendor "Joseph Heydorn" ^
--app-version "%nortantis_version%" ^
--java-options -XX:MaxRAMPercentage=50.0 ^
--java-options -XX:MaxHeapFreeRatio=40 ^
--java-options -XX:MinHeapFreeRatio=20 ^
--java-options -XX:G1PeriodicGCInterval=15000 ^
--java-options -Dsun.java2d.d3d=false ^
--license-file end_user_license_agreement.txt ^
--java-options -Dfile.encoding=UTF-8


REM Line to create Windows console: --win-console ^

REM line to add license file: --license-file end_user_license_agreement.txt ^

RMDIR /S /Q installer_input
REM DEL "..\Nortantis.jar"
