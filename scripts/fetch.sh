#!/usr/bin/env bash
wget https://github.com/acron0/espdig/archive/master.zip -O espdig.zip
unzip -o espdig.zip
rm espdig.zip
cd espdig-master
./scripts/build.sh
