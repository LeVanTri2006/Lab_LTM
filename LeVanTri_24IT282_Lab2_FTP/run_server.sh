#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR" || exit 1

mkdir -p bin
javac -encoding UTF-8 -cp ".:lib/*" -d bin $(find src -name "*.java") || exit 1

echo "[VKU - LTM] Khởi động FTP Server (Port 2121 + MySQL Support)..."
java -cp "bin:lib/*" com.vku.ftp.server.FtpServerGUI
