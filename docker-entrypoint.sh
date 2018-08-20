#!/bin/bash
set -e

# test if first command is no argument and executable
if [ "${1#-}" == "$1" ] && command -v "$1" &> /dev/null; then
  exec "$@"
fi

exec wfs-ft-validator "$@"