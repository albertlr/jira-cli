#!/usr/bin/env bash
#set -x

# in base64 we have the following "jiraUser:jiraPassword"
if [ -f "${HOME}/.jira_auth" ]; then
#  echo "Using ${HOME}/.jira_auth for authentication"
  export AUTH=$(cat ${HOME}/.jira_auth | base64 --decode)
else
#  echo "NO ${HOME}/.jira_auth used authentication"
  AUTH=$(echo -n $JIRA_AUTH | base64 --decode)
fi
