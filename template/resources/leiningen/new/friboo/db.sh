#!/usr/bin/env bash
set -euo pipefail
IFS=$'\t\n'

IMAGE=postgres
CONTAINER={{name}}-db
PORT=5432

cmd_drm() {
    docker rm -fv ${CONTAINER} ||  true
}

cmd_drun() {
    cmd_drm
    docker run -dt --name ${CONTAINER} \
        -p ${PORT}:5432 \
        ${IMAGE}
}

cmd_dshell() {
    docker exec -it ${CONTAINER} bash
}

cmd_dlogs() {
    docker logs -f ${CONTAINER}
}

# Shortcuts
cmd_r() { cmd_drun; }
cmd_l() { cmd_dlogs; }
cmd_s() { cmd_dshell; }

# Print all defined cmd_
cmd_help() {
    compgen -A function cmd_
}

# Run multiple commands without args
cmd_mm() {
    for cmd in "$@"; do
        cmd_$cmd
    done
}

if [[ $# -eq 0 ]]; then
    echo Please provide a subcommand
    exit 1
fi

SUBCOMMAND=$1
shift

# Enable verbose mode
set -x
# Run the subcommand
cmd_${SUBCOMMAND} $@
