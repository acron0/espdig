#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

exec java ${PEER_JAVA_OPTS:-} -jar /srv/espdig.jar
