#!/usr/bin/env bash
#set -x

source jira-all-links-of-fa.sh

faId="$1"
# get links of an FA that are linking to JCLDALL
linksToDelete=$(allLinksOfFA "$faId" "JCLDALL")

./jira-delete-link.sh "$faId" "$linksToDelete"