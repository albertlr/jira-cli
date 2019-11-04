#!/usr/bin/env bash

source prepare-env.sh

java -jar target/jira-cli-1.0-SNAPSHOT-jar-with-dependencies.jar --action get-transitions --source "$1"