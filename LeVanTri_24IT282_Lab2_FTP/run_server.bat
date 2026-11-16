@echo off
cd /d "%~dp0" || exit /b 1
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động FTP Server (Port 2121 + MySQL Support)...
if not exist "bin" mkdir bin
javac -encoding UTF-8 -cp ".;lib/*" -d bin src/com/vku/ftp/common/*.java src/com/vku/ftp/db/*.java src/com/vku/ftp/server/*.java src/com/vku/ftp/client/*.java
if errorlevel 1 exit /b 1
start "VKU FTP Server Manager" java -cp "bin;lib/*" com.vku.ftp.server.FtpServerGUI
