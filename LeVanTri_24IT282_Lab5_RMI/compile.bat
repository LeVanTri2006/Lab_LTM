@echo off
setlocal
cd /d "%~dp0"
chcp 65001 > nul
echo [VKU - LTM] Dang bien dich Lab 5 RMI...
if not exist "bin" mkdir bin
javac --release 17 -encoding UTF-8 -cp "lib/*" -d bin src/com/vku/rmi/model/*.java src/com/vku/rmi/service/*.java src/com/vku/rmi/db/*.java src/com/vku/rmi/server/*.java src/com/vku/rmi/client/*.java src/com/vku/rmi/monitor/*.java src/com/vku/rmi/ui/*.java
if errorlevel 1 (
    echo [!] Bien dich that bai.
    exit /b 1
)
echo [OK] Da bien dich vao thu muc bin.
exit /b 0
