#!/usr/bin/env bash
# Serves the two portals on http://localhost:4200 with the JDK's built-in web server (no Node.js needed).
# 4200 must match the Keycloak redirect URIs (platform-console, erp-web) and the backends' CORS origin.
set -euo pipefail
cd "$(dirname "$0")"
DIR=$(pwd -W 2>/dev/null || pwd)   # Windows path under Git Bash
echo "Platform console : http://localhost:4200/platform/"
echo "Company portal   : http://localhost:4200/tenant/"
# same as `jwebserver`, which is often not on the PATH while `java` is (JDK 18+)
exec java -m jdk.httpserver -p "${PORT:-4200}" -b 127.0.0.1 -d "$DIR"
