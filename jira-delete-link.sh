#!/usr/bin/env bash
#set -x

source setup-auth.sh

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

for fromDefectId in $(echo "${1}" | sed "s/,/ /g")
do

  curl  "https://jira.devfactory.com/rest/api/2/issue/${fromDefectId}" --user "${AUTH}" | jq > $issueData
  internalFromDefectId=$(cat $issueData | jq --raw-output '.id')

  for toDefectId in $(echo "${2}" | sed "s/,/ /g")
  do
    # eg: delivers item to
    destination=$( cat $issueData | jq ".fields.issuelinks[] | select(.inwardIssue.key==\"$toDefectId\") | {id: .id, key: .inwardIssue.key, linkTypeId: .type.id, issueId: .inwardIssue.id}" )

    if [ -z "$destination" ]; then
      # vs will be delivered by
      destination=$( cat $issueData | jq ".fields.issuelinks[] | select(.outwardIssue.key==\"$toDefectId\") | {id: .id, key: .outwardIssue.key, linkTypeId: .type.id, issueId: .outwardIssue.id}" )
      echo $destination

      linkId=$( echo $destination | jq -r '.id' )
      linkTypeId=$( echo $destination | jq -r '.linkTypeId' )
      destId=$( echo $destination | jq -r '.issueId' )

      curl "https://jira.devfactory.com/secure/DeleteLink.jspa?atl_token=${CSRF}" \
         --user "${AUTH}" \
         --cookie $cookieJar \
         --data "id=${internalFromDefectId}&destId=${destId}&linkType=${linkTypeId}&inline=true&decorator=dialog&confirm=true&atl_token=${CSRF}"

    else
      echo $destination

      linkId=$( echo $destination | jq -r '.id' )
      linkTypeId=$( echo $destination | jq -r '.linkTypeId' )
      sourceId=$( echo $destination | jq -r '.issueId' )

      curl "https://jira.devfactory.com/secure/DeleteLink.jspa?atl_token=${CSRF}" \
         --user "${AUTH}" \
         --cookie $cookieJar \
         --data "id=${internalFromDefectId}&sourceId=${sourceId}&linkType=${linkTypeId}&inline=true&decorator=dialog&confirm=true&atl_token=${CSRF}"

    fi
  done
done