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
--app-version "0.1" ^
--java-options -XX:MaxRAMPercentage=50.0 ^
--java-options -Dfile.encoding=UTF-8 ^
--license-file end_user_license_agreement.txt

RMDIR /S /Q installer_input


pause