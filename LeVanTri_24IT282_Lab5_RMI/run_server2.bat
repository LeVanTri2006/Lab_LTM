@echo off
setlocal
cd /d "%~dp0"
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động Server 2 (Port RMI: 1100, Partner: 1099)...
if not exist "bin\com\vku\rmi\server\RmiSyncServer.class" (
    echo Đang biên dịch mã nguồn trước...
    call compile.bat
    if errorlevel 1 exit /b 1
)
start "VKU RMI Server 2 (Port 1100)" java -cp "bin;lib/*" com.vku.rmi.server.RmiSyncServer -id SERVER_2 -host 127.0.0.1 -port 1100 -partnerHost 127.0.0.1 -partnerPort 1099 -partnerId SERVER_1
