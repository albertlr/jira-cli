#!/usr/bin/env bash
#set -x

source setup-auth.sh

issueData=issueData.json

function allLinksOfFA() {
  issueId=$1
  projectId=$2
  for fromDefectId in $(echo "${issueId}" | sed "s/,/ /g"); do

    curl "https://jira.devfactory.com/rest/api/2/issue/${fromDefectId}" --user "${AUTH}" | jq >${fromDefectId}.json
    # get all linked issues of a FA that are "is covered by" and they are in JCLDALL project
    allJCLDALLKeys=$(cat ${fromDefectId}.json | jq --raw-output ".fields.issuelinks | .[] | {out: .outwardIssue.key, outType: .type.outward, in: .inwardIssue.key, inType: .type.inward} | select(.outType|contains(\"cover\")) | select(.out|startswith(\"$projectId\")) | .out")

    echo $(echo $allJCLDALLKeys | sed "s/ /,/g")
  done
}

#allLinksOfFA $1 $2