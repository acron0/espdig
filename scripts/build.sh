#!/usr/bin/env bash
lein clean
lein uberjar
docker build -t acron/espdig .
