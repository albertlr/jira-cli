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
package ro.albertlr.jira;

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;

@UtilityClass
public class IssueLogger {

    public static void logIssue(Logger log, Issue issue) {
        logIssue(log, issue, true);
    }

    public static void logIssue(Logger log, Issue issue, boolean logLinks) {
        log.info("Issue '{}' {} - {}", issue.getIssueType().getName(), issue.getKey(), issue.getSummary());
        if (logLinks) {
            for (IssueLink link : issue.getIssueLinks()) {
                log.info("      {} -> {} '{}':{}", issue.getKey(), link.getTargetIssueKey(),
                        link.getIssueLinkType().getName(), link.getIssueLinkType().getDirection());
            }
        }
    }

    public static void logIssue(Logger log, BasicIssue issue) {
        log.info("Issue {} - {}", issue.getKey(), issue.getSelf());
    }

}
