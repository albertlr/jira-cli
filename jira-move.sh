#!/usr/bin/env bash

java -jar target/jira-cli-1.0-SNAPSHOT-jar-with-dependencies.jar --action move --source $1 --project ${2:-JVCLD}