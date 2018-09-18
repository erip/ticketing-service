#!/usr/bin/env sh

function find_command() {
  COMMAND="$1"
  echo "Searching for $COMMAND... "

  # `command -v` is POSIX compliant, but `which` is not.
  COMMAND_PATH=`command -v "$COMMAND"`

  if [ ! $? -eq 0 ]; then
    echo "Couldn't find $COMMAND"
    exit 1
  fi

  echo "Found $COMMAND at $COMMAND_PATH"
}

function find_image() {
  # Get directory containing this script, irrespective of execution site.
  # See https://stackoverflow.com/a/246128/2883245
  DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  FOUND_IMAGE=`docker images | grep erip`

  if [ ! $? -eq 0 ]; then
    echo "Couldn't find image... Try building it with $DIR/build_docker_image.sh"
    exit 1
  fi
}

function run_container() {
  docker run -it --rm -p 9000:9000 'erip-homework' "$@"
}

function run_sbt() {
  CUR_DIR="$PWD"

  # Get directory containing this script, irrespective of execution site.
  # See https://stackoverflow.com/a/246128/2883245
  DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

  cd "$DIR/.."
  sbt "$@"
  cd "$CUR_DIR"
}
