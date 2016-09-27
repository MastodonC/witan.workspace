#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

export BIND_ADDR="${BIND_ADDR:-$(hostname --ip-address)}"
export APP_NAME=$(echo "witan.workspace" | sed s/"-"/"_"/g)
exec java -Djava.awt.headless=true -jar /srv/witan.workspace.jar
