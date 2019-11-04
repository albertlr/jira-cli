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
import com.atlassian.jira.rest.client.api.domain.Transition;
import io.atlassian.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Configuration;
import ro.albertlr.jira.Configuration.IssueTypeConfig;
import ro.albertlr.jira.Jira;
import ro.albertlr.jira.Utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class AutoTransitionIssue implements Action<Void> {

    private final Configuration configuration = Configuration.loadConfiguration();

    @Override
    public Void execute(Jira jira, String... params) {
        String issueKeys = Action.paramAt(params, 0, "issueKey");
        String phase = Action.paramAt(params, 0, "phase");

        for (String issueKey : Utils.split(issueKeys)) {
            Issue issue = jira.loadIssue(issueKey);

            IssueTypeConfig typeConfig = configuration.configFor(issue.getIssueType().getName());
            Collection<String> phases = typeConfig.getTransitionFlow(phase)
                    .stream()
                    .map(phaseChoice -> Utils.splitToList(phaseChoice, '|'))
                    .map(Collection::stream)
                    .flatMap(Function.identity())
                    .collect(Collectors.toList());

            doTransition(jira, issue, phases);
        }

        return null;
    }

    private void doTransition(Jira jira, Issue issue, Collection<String> phases) {
        Iterable<Transition> transactions = jira.loadTransitionsFor(issue);
        Iterator<String> phaseIterator = phases.iterator();
        Transition transitionTo = null;
        phaseIt:
        while (phaseIterator.hasNext()) {
            String phase = phaseIterator.next();
            for (Transition transition : transactions) {
                if (transition.getName().equals(phase)) {
                    transitionTo = transition;
                    break phaseIt;
                }
            }
        }

        if (transitionTo != null) {
            log.info("Transition {} - {} to {} {}", issue.getKey(), issue.getSummary(),
                    transitionTo.getId(), transitionTo.getName());
            Promise<Void> transitioning = jira.transitionIssue(issue, transitionTo);
            transitioning.claim();

            doTransition(jira, issue, phases);
        } else {
            log.info("No more transition phases for {} - {}", issue.getKey(), issue.getSummary());
        }
    }

}
