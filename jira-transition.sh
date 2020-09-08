#!/usr/bin/env bash

script=$(basename $0)
current_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

pushd $current_dir

source prepare-env.sh

java -jar target/jira-cli-1.0-SNAPSHOT-jar-with-dependencies.jar --action advance-issue --source "$1"
popd
