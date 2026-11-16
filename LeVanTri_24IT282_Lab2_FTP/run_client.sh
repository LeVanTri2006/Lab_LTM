#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR" || exit 1

mkdir -p bin
javac -encoding UTF-8 -d bin src/com/vku/ftp/client/*.java || exit 1

echo "[VKU - LTM] Đang khởi động FTP Client..."
java -cp bin com.vku.ftp.client.FtpClientGUI

