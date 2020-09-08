/*-
 * #%L
 * jira-cli
 *  
 * Copyright (C) 2019 László-Róbert, Albert (robert@albertlr.ro)
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ro.albertlr.jira.action;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType.Direction;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.google.common.collect.Lists;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Jira;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ro.albertlr.jira.Action.paramAt;
import static ro.albertlr.jira.CLI.COVERS_LINK;
import static ro.albertlr.jira.CLI.DEPENDS_ON_LINK;
import static ro.albertlr.jira.CLI.ISSUE_E2E;
import static ro.albertlr.jira.CLI.ISSUE_TYPE_CUSTOMER_DEFECT;
import static ro.albertlr.jira.CLI.ISSUE_TYPE_DEFECT;
import static ro.albertlr.jira.CLI.ISSUE_TYPE_FEATURE_DEFECT;
import static ro.albertlr.jira.CLI.ISSUE_TYPE_FEATURE_STORY;
import static ro.albertlr.jira.CLI.TESTED_BY_LINK;
import static ro.albertlr.jira.Utils.split;

public class GetE2EsRecursively implements Action<Map<String, Set<Issue>>> {
    @Override
    public Map<String, Set<Issue>> execute(Jira jira, String... params) {
        String jiraSourceKey = paramAt(params, 0, "sourceKey");
        boolean recursive = Boolean.valueOf(paramAt(params, 1, "recursive"));

        Map<String, Set<Issue>> e2es = doGetIssueE2Es(jira, jiraSourceKey);
        if (recursive) {
            Set<String> keysProcessed = new HashSet<>();
            keysProcessed.addAll(e2es.keySet());
            Queue<Set<Issue>> issues = Lists.newLinkedList(e2es.values());
            while (!issues.isEmpty()) {
                Set<Issue> nextIterationIssueKeys = issues.poll();
                doGetIssueE2Es(jira, nextIterationIssueKeys.stream().map(Issue::getKey).collect(Collectors.toSet()), e2es);

                for (String potentiallyUnprocessedKey : e2es.keySet()) {
                    if (!keysProcessed.contains(potentiallyUnprocessedKey)) {
                        issues.offer(e2es.get(potentiallyUnprocessedKey));
                    }
                }

                keysProcessed.addAll(nextIterationIssueKeys.stream().map(Issue::getKey).collect(Collectors.toSet()));
            }
        }

        return e2es;
    }

    private static Map<String, Set<Issue>> doGetIssueE2Es(Jira jira, String issueKeys) {
        Iterable<String> issues = split(issueKeys);
        return doGetIssueE2Es(jira, issues);
    }

    private static Map<String, Set<Issue>> doGetIssueE2Es(Jira jira, Iterable<String> issueKeys) {
        Map<String, Set<Issue>> e2es = new LinkedHashMap<>();
        return doGetIssueE2Es(jira, issueKeys, e2es);
    }

    private static Map<String, Set<Issue>> doGetIssueE2Es(Jira jira, Iterable<String> issueKeys, Map<String, Set<Issue>> e2es) {
        for (String issueKey : issueKeys) {
            if (e2es.containsKey(issueKey)) {
                continue;
            }

            Issue issue = jira.loadIssue(issueKey);

            Set<Issue> e2esOf = new TreeSet<>((o1, o2) -> {
                if (o1 != null && o2 != null) {
                    return o1.getKey().compareTo(o2.getKey());
                }

                if (o1 == null && o2 == null) {
                    return 0;
                }

                return o1 == null ? -1 : 1;
            });
            e2es.put(issueKey, e2esOf);

            // if is an E2E then found the "Depends On" links
            if (isTestableType(issue.getIssueType())) {
                for (IssueLink link : Jira.safe(issue.getIssueLinks())) {
                    if (isDependsOnLink(link.getIssueLinkType())) {
                        String e2eIssueKey = link.getTargetIssueKey();
                        Issue e2eIssue = jira.loadIssue(e2eIssueKey);
                        // we are interested only in E2E dependencies
                        if (isE2e(e2eIssue.getIssueType())) {
                            e2esOf.add(e2eIssue);
                        }
                    }
                }
            } else {
                // if Defect, Customer Defect, Feature Story or Feature Defect then found the tested by links
                if (isReleasableType(issue.getIssueType())) {
                    boolean hasTestedBy = false;
                    for (IssueLink link : Jira.safe(issue.getIssueLinks())) {
                        if (isTestedByLink(link.getIssueLinkType())) {
                            String e2eIssueKey = link.getTargetIssueKey();
                            Issue e2eIssue = jira.loadIssue(e2eIssueKey);
                            // we are interested only in E2E dependencies
                            if (isE2e(e2eIssue.getIssueType())) {
                                e2esOf.add(e2eIssue);
                            }
                        }
                    }
                }
            }
        }

        return e2es;
    }

    public static boolean isReleasableType(IssueType issueType) {
        return isOfType(
                issueType,
                Arrays.asList(
                        GetE2EsRecursively::isDefect,
                        GetE2EsRecursively::isCustomerDefect,
                        GetE2EsRecursively::isFeatureStory,
                        GetE2EsRecursively::isFeatureDefect
                )
        );
    }

    private static boolean isTestableType(IssueType issueType) {
        return isOfType(
                issueType,
                Arrays.asList(
                        GetE2EsRecursively::isE2e
                )
        );
    }

    private static boolean isOfType(IssueType issueType, Collection<Predicate<IssueType>> issueTypeChecks) {
        for (Predicate<IssueType> check : issueTypeChecks) {
            if (check.test(issueType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isE2e(IssueType issueType) {
        return ISSUE_E2E.equals(issueType.getName());
    }

    public static boolean isCustomerDefect(IssueType issueType) {
        return ISSUE_TYPE_CUSTOMER_DEFECT.equals(issueType.getName());
    }

    public static boolean isDefect(IssueType issueType) {
        return ISSUE_TYPE_DEFECT.equals(issueType.getName());
    }

    public static boolean isFeatureStory(IssueType issueType) {
        return ISSUE_TYPE_FEATURE_STORY.equals(issueType.getName());
    }

    public static boolean isFeatureDefect(IssueType issueType) {
        return ISSUE_TYPE_FEATURE_DEFECT.equals(issueType.getName());
    }

    public static boolean isDependsOnLink(IssueLinkType issueLinkType) {
        return DEPENDS_ON_LINK.equals(issueLinkType.getName())
                && Direction.OUTBOUND.equals(issueLinkType.getDirection());
    }

    public static boolean isDependentOnByLink(IssueLinkType issueLinkType) {
        return DEPENDS_ON_LINK.equals(issueLinkType.getName())
                && Direction.INBOUND.equals(issueLinkType.getDirection());
    }

    public static boolean isTestedByLink(IssueLinkType issueLinkType) {
        return TESTED_BY_LINK.equals(issueLinkType.getName())
                && Direction.OUTBOUND.equals(issueLinkType.getDirection());
    }

    public static boolean isTestsLink(IssueLinkType issueLinkType) {
        return TESTED_BY_LINK.equals(issueLinkType.getName())
                && Direction.INBOUND.equals(issueLinkType.getDirection());
    }

    public static boolean isCoversLink(IssueLinkType issueLinkType) {
        return COVERS_LINK.equals(issueLinkType.getName())
                && Direction.OUTBOUND.equals(issueLinkType.getDirection());
    }

    public static boolean isCoveredByLink(IssueLinkType issueLinkType) {
        return COVERS_LINK.equals(issueLinkType.getName())
                && Direction.INBOUND.equals(issueLinkType.getDirection());
    }


}
