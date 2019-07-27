#!/bin/sh
set +x

exec >${LOGS_PATH}/stdout.log
exec 2>${LOGS_PATH}/stderr.log

exec "$@"
