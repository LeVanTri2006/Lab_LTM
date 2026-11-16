@echo off
setlocal
cd /d "%~dp0"
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động 4 Client mô phỏng nhiều máy ảo truy cập cụm RMI...
if not exist "bin\com\vku\rmi\client\RmiClientGUI.class" (
    echo Đang biên dịch mã nguồn trước...
    call compile.bat
    if errorlevel 1 exit /b 1
)

start "RMI Client 1 (VM-1)" java -cp "bin;lib/*" com.vku.rmi.client.RmiClientGUI 127.0.0.1 1099 127.0.0.1 1100
timeout /t 1 > nul
start "RMI Client 2 (VM-2)" java -cp "bin;lib/*" com.vku.rmi.client.RmiClientGUI 127.0.0.1 1100 127.0.0.1 1099
timeout /t 1 > nul
start "RMI Client 3 (VM-3)" java -cp "bin;lib/*" com.vku.rmi.client.RmiClientGUI 127.0.0.1 1099 127.0.0.1 1100
timeout /t 1 > nul
start "RMI Client 4 (VM-4)" java -cp "bin;lib/*" com.vku.rmi.client.RmiClientGUI 127.0.0.1 1100 127.0.0.1 1099

echo [✓] Đã khởi động 4 Client thành công!
pause
