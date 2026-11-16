@echo off
setlocal
cd /d "%~dp0"
call "%~dp0compile.bat" || exit /b 1
start "VKU Web Server (HTTP/1.1)" java -cp "target/classes;target/dependency/*" com.vku.web.server.WebServerGUI
