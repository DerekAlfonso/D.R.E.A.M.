#!/usr/bin/env bash
# Launches D.R.E.A.M. Builds the jar first if it is missing.
set -e
cd "$(dirname "$0")"

if [ ! -f target/dream.jar ]; then
  echo "Building..."
  mvn -q -B package
fi

exec java -jar target/dream.jar "$@"
