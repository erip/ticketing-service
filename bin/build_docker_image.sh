#!/usr/bin/env sh

# Get directory containing this script, irrespective of execution site.
# See https://stackoverflow.com/a/246128/2883245
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

# Get helper functions
source $DIR/utilities.sh

# Check if docker exists, otherwise error out. 
find_command "docker"

docker build -t 'erip-homework' "$DIR/.."
