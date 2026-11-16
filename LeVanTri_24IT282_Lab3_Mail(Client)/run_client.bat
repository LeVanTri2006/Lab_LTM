@echo off
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động Mail Client (Tương tự Gmail)...
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -cp "lib/*" -sourcepath src -d bin src/com/vku/mail/client/MailClientGUI.java
if errorlevel 1 exit /b 1
start "VKU Mail Client" java -cp "bin;lib/*" com.vku.mail.client.MailClientGUI
