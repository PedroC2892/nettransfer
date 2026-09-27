@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%target\nettransfer-1.0.jar

set M2=%USERPROFILE%\.m2\repository\org\openjfx
set VER=21.0.4

set FX_MODS=%M2%\javafx-base\%VER%\javafx-base-%VER%-win.jar;%M2%\javafx-base\%VER%\javafx-base-%VER%.jar;%M2%\javafx-controls\%VER%\javafx-controls-%VER%-win.jar;%M2%\javafx-controls\%VER%\javafx-controls-%VER%.jar;%M2%\javafx-fxml\%VER%\javafx-fxml-%VER%-win.jar;%M2%\javafx-fxml\%VER%\javafx-fxml-%VER%.jar;%M2%\javafx-graphics\%VER%\javafx-graphics-%VER%-win.jar;%M2%\javafx-graphics\%VER%\javafx-graphics-%VER%.jar

java --module-path "%FX_MODS%" --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics --enable-native-access=javafx.graphics -jar "%JAR%" %*
endlocal
