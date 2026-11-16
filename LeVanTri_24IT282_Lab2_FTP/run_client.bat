@echo off
cd /d "%~dp0" || exit /b 1
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động FTP Client...
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin src/com/vku/ftp/client/*.java
if errorlevel 1 exit /b 1
start "VKU FTP Client" java -cp bin com.vku.ftp.client.FtpClientGUI
