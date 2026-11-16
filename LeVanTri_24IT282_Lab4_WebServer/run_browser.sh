#!/bin/bash
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
bash ./compile.sh || exit 1
exec java -cp "target/classes:target/dependency/*" com.vku.web.browser.MiniBrowserGUI
