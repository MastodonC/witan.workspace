#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

export BIND_ADDR="${BIND_ADDR:-$(hostname --ip-address)}"
export APP_NAME=$(echo "witan.workspace" | sed s/"-"/"_"/g)
exec java ${PEER_JAVA_OPTS:-} -jar /srv/witan.workspace.jar
