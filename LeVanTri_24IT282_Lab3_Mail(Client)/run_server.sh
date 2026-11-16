#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR" || exit 1

mkdir -p bin
javac -encoding UTF-8 -cp "lib/*" -sourcepath src -d bin src/com/vku/mail/server/MailServerGUI.java || exit 1

echo "[VKU - LTM] Khởi động Mail Server (SMTP: 2525, POP3: 1100 + MySQL Support)..."
java -cp "bin:lib/*" com.vku.mail.server.MailServerGUI
