#!/usr/bin/env sh

# Get directory containing this script, irrespective of execution site.
# See https://stackoverflow.com/a/246128/2883245
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

source "$DIR/utilities.sh"

find_command "docker"

find_image

run_container
