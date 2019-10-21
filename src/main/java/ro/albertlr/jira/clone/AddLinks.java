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
package ro.albertlr.jira.clone;

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType.Direction;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.google.common.io.Files;
import io.atlassian.util.concurrent.Promise.TryConsumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import ro.albertlr.jira.Action.Name;
import ro.albertlr.jira.Configuration;
import ro.albertlr.jira.Configuration.ActionConfig;
import ro.albertlr.jira.Jira;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class AddLinks implements TryConsumer<BasicIssue> {
    private final Jira jira;
    private final Issue source;
    private final Configuration configuration;

    @Override
    public void fail(@Nonnull Throwable t) {
        log.error("Cannot add link from {}", source.getKey(), t);
    }

    @Override
    public void accept(BasicIssue basicIssue) {
        final String issueKey = basicIssue.getKey();
        // now mark it as clone
        LinkIssuesInput cloneLink = new LinkIssuesInput(issueKey, source.getKey(), "Cloners");
        log.info("Link {} to {} as {}", cloneLink.getFromIssueKey(), cloneLink.getToIssueKey(), cloneLink.getLinkType());
        doLink(cloneLink, issueKey);

        for (IssueLink link : Jira.safe(source.getIssueLinks())) {
            Pair<String, String> linkPair = buildLinkPair(issueKey, link);

            LinkIssuesInput linkInput = new LinkIssuesInput(linkPair.getOne(), linkPair.getTwo(), link.getIssueLinkType().getName());
            log.info("Link {} to {} as {}", linkInput.getFromIssueKey(), linkInput.getToIssueKey(), linkInput.getLinkType());
            doLink(linkInput, issueKey);
        }
    }

    private Pair<String, String> buildLinkPair(String issueKey, IssueLink link) {
        String from;
        String to;
        if (Direction.INBOUND.equals(link.getIssueLinkType().getDirection())) {
            from = issueKey;
            to = link.getTargetIssueKey();
        } else {
            from = link.getTargetIssueKey();
            to = issueKey;
        }
        return Tuples.pair(from, to);
    }

    private void doLink(LinkIssuesInput link, String issueKey) {
        ActionConfig config = configuration.getActionConfigs().get(Name.CLONE);
        if ("generateScript".equals(config.getProperty("action.clone.links.action"))) {
            String script =
                    String.format(
                            config.getProperty("action.clone.links.action.generateScript.script", "links-for-%s.sh"),
                            issueKey
                    );

            StringBuilder command = new StringBuilder(128);
            command.append("./jira-link.sh ")
                    .append(link.getFromIssueKey())
                    .append(" ")
                    .append(link.getToIssueKey())
                    .append(" \"")
                    .append(unnormalizeLinkType(link.getLinkType()))
                    .append("\"");

            try {
                log.info("$ {}", command);
                Files.write(command, new File(script), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Could not write command [{}] to script {}", command, script);
            }
        } else {
            jira.link(link);
        }
    }

    private String unnormalizeLinkType(String linkType) {
        for (Map.Entry<String, String> type : configuration.getLinkTypes().entrySet()) {
            if (type.getKey().equals(linkType) || type.getValue().equals(linkType)) {
                return type.getKey();
            }
        }
        return linkType;
    }

}
