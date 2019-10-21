#!/usr/bin/env bash

source prepare-env.sh

java -jar target/jira-cli-1.0-SNAPSHOT-jar-with-dependencies.jar --action clone --source "$1"

if [ $? -eq 0 ]; then
    IFS=',' read -ra issuesToLink <<< "$1"
    for issueToLink in "${issuesToLink[@]}"; do
        script="links-for-${issueToLink}.sh"
        if [ -f "$script" ]; then
            source ${script}
        fi
    done
else
    exit 1
fi