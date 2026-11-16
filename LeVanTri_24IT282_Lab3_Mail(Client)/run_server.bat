@echo off
chcp 65001 > nul
echo [VKU - LTM] Đang khởi động Mail Server (SMTP: 2525, POP3: 1100 + MySQL Support)...
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -cp "lib/*" -sourcepath src -d bin src/com/vku/mail/server/MailServerGUI.java
if errorlevel 1 exit /b 1
start "VKU Mail Server" java -cp "bin;lib/*" com.vku.mail.server.MailServerGUI
