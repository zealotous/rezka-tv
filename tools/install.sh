#!/bin/sh
# Installs the debug build on the Mijia projector and launches it.
set -e
IP="${1:-192.168.31.228:5555}"
cd "$(dirname "$0")/.."
adb connect "$IP"
adb -s "$IP" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$IP" shell am start -n me.rezka.tv/.ui.MainActivity
