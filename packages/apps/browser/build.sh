#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# The shared builder also prepares this application's Chromium runtime.
exec bash "$APP_DIR/../build.sh" browser
