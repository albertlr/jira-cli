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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import ro.albertlr.jira.Action.Name;
import ro.albertlr.jira.CLI;
import ro.albertlr.jira.Configuration;
import ro.albertlr.jira.Configuration.ActionConfig;
import ro.albertlr.jira.Jira;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
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

        Strategy strategy = selectStartegy(issueKey);

        LinkIssuesInput cloneLink = new LinkIssuesInput(issueKey, source.getKey(), "Cloners");
        log.info("Link {} to {} as {}", cloneLink.getFromIssueKey(), cloneLink.getToIssueKey(), cloneLink.getLinkType());
        strategy.collect(cloneLink);

        for (IssueLink link : Jira.safe(source.getIssueLinks())) {
            Pair<String, String> linkPair = buildLinkPair(issueKey, link);

            LinkIssuesInput linkInput = new LinkIssuesInput(linkPair.getOne(), linkPair.getTwo(), link.getIssueLinkType().getName());
            log.info("Link {} to {} as {}", linkInput.getFromIssueKey(), linkInput.getToIssueKey(), linkInput.getLinkType());
            strategy.collect(linkInput);
        }

        strategy.execute();
    }

    private Strategy selectStartegy(String issueKey) {
        Strategy strategy;
        ActionConfig config = configuration.getActionConfigs().get(Name.CLONE);
        String script = null;
        if ("generateScript".equals(config.getProperty("action.clone.links.strategy"))) {
            script =
                    String.format(
                            config.getProperty("action.clone.links.strategy.generateScript.script", "links-for-%s.sh"),
                            issueKey
                    );
            strategy = new GenerateScriptStrategy(script);
        } else if ("invokeInSameProcess".equals(config.getProperty("action.clone.links.strategy"))) {
            strategy = new InvokeInSameJvmStrategy();
        } else {
            strategy = new LinkIssueOnCollectStrategy(jira);
        }
        return strategy;
    }

    private Pair<String, String> buildLinkPair(String issueKey, IssueLink link) {
        String from;
        String to;
        if (Direction.OUTBOUND.equals(link.getIssueLinkType().getDirection())) {
            from = issueKey;
            to = link.getTargetIssueKey();
        } else {
            from = link.getTargetIssueKey();
            to = issueKey;
        }
        return Tuples.pair(from, to);
    }


    @RequiredArgsConstructor
    private class InvokeInSameJvmStrategy implements Strategy {
        private final Collection<LinkIssuesInput> linksToExecute = new ArrayList<>();

        public void collect(LinkIssuesInput link) {
            linksToExecute.add(link);
        }

        @Override
        public void execute() {
            for (LinkIssuesInput link : linksToExecute) {
                CLI.execute(
                        "--action", "link",
                        "--source", link.getFromIssueKey(),
                        "--target", link.getToIssueKey(),
                        "--link-type", unnormalizeLinkType(link.getLinkType()));
            }
        }
    }

    @RequiredArgsConstructor
    private class LinkIssueOnCollectStrategy implements Strategy {
        private final Jira jira;

        public void collect(LinkIssuesInput link) {
            jira.link(link);
        }

        @Override
        public void execute() {
            // do nothing
        }
    }

    @RequiredArgsConstructor
    private class GenerateScriptStrategy implements Strategy {
        private final StringBuilder commandBuilder = new StringBuilder(512);
        private final String script;

        public void collect(LinkIssuesInput link) {
            StringBuilder command = new StringBuilder(32);
            command.append("./jira-link.sh ")
                    .append(link.getFromIssueKey())
                    .append(" ")
                    .append(link.getToIssueKey())
                    .append(" \"")
                    .append(unnormalizeLinkType(link.getLinkType()))
                    .append("\"")
                    .append(System.lineSeparator());

            log.info("$ {}", command);

            commandBuilder.append(command);
        }

        @Override
        public void execute() {
            try {
                Files.write(commandBuilder, new File(script), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Could not write script {}", script, e);
            }
        }
    }

    interface Strategy {
        void collect(LinkIssuesInput linkIssuesInput);

        void execute();
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
