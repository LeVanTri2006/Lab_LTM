@echo off
setlocal
cd /d "%~dp0"
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động RMI Client...
if not exist "bin\com\vku\rmi\client\RmiClientGUI.class" (
    echo Đang biên dịch mã nguồn trước...
    call compile.bat
    if errorlevel 1 exit /b 1
)
start "VKU RMI Client" java -cp "bin;lib/*" com.vku.rmi.client.RmiClientGUI %*
