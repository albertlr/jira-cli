#!/usr/bin/env bash
set -ex

# in base64 we have the following "jiraUser:jiraPassword"
if [ -f "${HOME}/.jira_auth" ]; then
  echo "Using ${HOME}/.jira_auth for authentication"
  export AUTH=$(cat ${HOME}/.jira_auth | base64 --decode)
else
  echo "NO ${HOME}/.jira_auth used authentication"
  AUTH=$(echo -n $JIRA_AUTH | base64 --decode)
fi

fromDefectId=${1}
toDefectId=${2}

#1.start session
cookieJar=./cookieJar.json
rm ${cookieJar} --force
issueData=issueData.json
rm ${issueData} --force

#2.login into jira
curl  'https://jira.devfactory.com' --user "${AUTH}" --cookie-jar "${cookieJar}" >/dev/null

#3.parse atlassian.xsrf.token cookie
CSRF=$(grep 'atlassian.xsrf.token' ${cookieJar} | cut -f7)
echo "CSRF is '${CSRF}'"

curl  "https://jira.devfactory.com/rest/api/2/issue/${fromDefectId}" --user "${AUTH}" | jq > $issueData

destination=$( cat $issueData | jq ".fields.issuelinks[] | select(.inwardIssue.key==\"$toDefectId\") | {id: .id, key: .inwardIssue.key, linkTypeId: .type.id, issueId: .inwardIssue.id}" )

echo $destination

linkId=$( echo $destination | jq -r '.id' )
linkTypeId=$( echo $destination | jq -r '.linkTypeId' )
sourceId=$( echo $destination | jq -r '.issueId' )


##4.get internal issue id
internalFromDefectId=$(cat $issueData | jq --raw-output '.id')
#echo "FROM ID is ${internalFromDefectId}"
#internalToDefectId=$(curl  "https://jira.devfactory.com/rest/api/2/issue/${toDefectId}" --user "${AUTH}" | jq --raw-output '.id')
#echo "TO ID is ${internalToDefectId}"
#
##6.fill & post form
curl "https://jira.devfactory.com/secure/DeleteLink.jspa?atl_token=${CSRF}" \
     --user "${AUTH}" \
     --cookie $cookieJar \
     --data "id=${internalFromDefectId}&sourceId=${sourceId}&linkType=${linkTypeId}&inline=true&decorator=dialog&confirm=true&atl_token=${CSRF}"

