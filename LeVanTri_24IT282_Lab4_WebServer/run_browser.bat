@echo off
setlocal
cd /d "%~dp0"
call "%~dp0compile.bat" || exit /b 1
start "VKU Mini Browser" java --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" com.vku.web.browser.MiniBrowserGUI
