#!/bin/sh

export HEROKU=true
export ENABLE_WEB=false

LATEST_BUILD="$(find ./build/libs | sort --version-sort --field-separator=- --key=2,2 | tail -n 1)"

until java -jar "${LATEST_BUILD}"; do
    echo "${LATEST_BUILD}"
    echo "BanUtil crashed with exit code $?.  Respawning.." >&2
    sleep 1
done
