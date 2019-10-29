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
import com.atlassian.jira.rest.client.api.domain.Status;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import ro.albertlr.jira.Action.Name;
import ro.albertlr.jira.Configuration.ActionConfig;

import java.util.Optional;

import static ro.albertlr.jira.Utils.split;

@UtilityClass
public class IssueLogger {

    private static final String BASIC_LOG_STRATEGY = "action.get.basic.properties";
    private static final String SHORT_LOG_STRATEGY = "action.get.short.properties";
    private static final String SHORT_WITH_LINKS_LOG_STRATEGY = "action.get.short-links.properties";
    private static final String FULL_LOG_STRATEGY = "action.get.full.properties";

    private static volatile Configuration configuration;

    private Configuration getConfiguration() {
        if (configuration == null) {
            synchronized (IssueLogger.class) {
                if (configuration == null) {
                    IssueLogger.configuration = Configuration.loadConfiguration();
                }
            }
        }
        return IssueLogger.configuration;
    }

    public static void fullLog(Logger log, Issue issue) {
        log.info("Issue {}", issueInfo(issue, FULL_LOG_STRATEGY));
    }

    public static void simpleLog(Logger log, Issue issue) {
        log.info("Issue {}", issueInfo(issue, SHORT_LOG_STRATEGY));
    }

    public static void shortLog(Logger log, Issue issue) {
        log.info("Issue {}", issueInfo(issue, SHORT_WITH_LINKS_LOG_STRATEGY));
    }

    public static void basicLog(Logger log, BasicIssue issue) {
        log.info("Issue {}", issueInfo(issue, BASIC_LOG_STRATEGY));
    }

    private static <I extends BasicIssue> StringBuilder issueInfo(I issue, String infoStrategy) {
        StringBuilder buffer = new StringBuilder(512);
        ActionConfig config = getConfiguration().getActionConfigs().get(Name.GET);

        Iterable<String> properties = split(config.getProperty(infoStrategy, ""));

        for (String property : properties) {
            switch (property) {
                case "key":
                    buffer.append(issue.getKey())
                            .append(" -");
                    break;
                case "self":
                    buffer.append(issue.getSelf());
                    break;

                case "type":
                    buffer.append('\'').append(((Issue) issue).getIssueType().getName()).append('\'');

                case "summary":
                    buffer.append(((Issue) issue).getSummary());
                    break;
                case "status": {
                    buffer.append('[')
                            .append(
                                    Optional.ofNullable(((Issue) issue).getStatus())
                                            .map(Status::getName)
                                            .orElse("<undefined>")
                            )
                            .append(']');
                }
                case "links": {
                    for (IssueLink link : ((Issue) issue).getIssueLinks()) {
                        buffer.append(System.lineSeparator());
                        buffer.append("    ").append(issue.getKey())
                                .append(" -> ").append(link.getTargetIssueKey())
                                .append(" '").append(link.getIssueLinkType().getName())
                                .append("':").append(link.getIssueLinkType().getDirection());
                    }
                }
                break;
            }

            buffer.append(' ');
        }

        return buffer;
    }

}
