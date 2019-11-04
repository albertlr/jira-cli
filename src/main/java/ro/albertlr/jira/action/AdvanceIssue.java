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
import com.google.common.collect.Lists;
import io.atlassian.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Configuration;
import ro.albertlr.jira.Configuration.IssueTypeConfig;
import ro.albertlr.jira.Jira;
import ro.albertlr.jira.Utils;

import java.util.Collection;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ro.albertlr.jira.action.AdvanceIssue.ChoiceStrategy.CONFIG_AND_BLOCK;
import static ro.albertlr.jira.action.AdvanceIssue.ChoiceStrategy.TERMINAL;
import static ro.albertlr.jira.action.BlockIssue.BLOCKED_PHASE;

@Slf4j
public class AdvanceIssue implements Action<Void> {

    @Override
    public Void execute(Jira jira, String... params) {
        String issueKeys = Action.paramAt(params, 0, "issueKey");
        String chooseStrategyParam = Action.paramAt(params, 1, "chooseStrategy", "TERMINAL");

        ChoiceStrategy choiceStrategy = ChoiceStrategy.valueOf(chooseStrategyParam);

        for (String issueKey : Utils.split(issueKeys)) {
            // get issue
            Issue issue = jira.loadIssue(issueKey);
            // get transitions of issue
            List<Transition> transitions = Lists.newArrayList(jira.loadTransitionsFor(issue));

            // choose where to go
            Transition chosen = null;
            while (chosen == null) {
                chosen = choiceStrategy.choose(issue, transitions);

                // transition the ticket
                if (chosen != null) {
                    Promise<Void> transitioning = jira.transitionIssue(issue, chosen);
                    transitioning.claim();
                    log.info("Issue {} - {} moved to {} - {}", issueKey, issue.getSummary(), chosen.getId(), chosen.getName());

                    if (CONFIG_AND_BLOCK.equals(choiceStrategy)
                            && chosen.getName().contains("Blocked")) {
                        chosen = null;
                    }
                } else {
                    chosen = TERMINAL.choose(issue, transitions);
                    if (chosen != null) {
                        Promise<Void> transitioning = jira.transitionIssue(issue, chosen);
                        transitioning.claim();
                        log.info("Issue {} - {} moved to {} - {}", issueKey, issue.getSummary(), chosen.getId(), chosen.getName());
                    }
                }
            }
        }

        return null;
    }

    public enum ChoiceStrategy {
        TERMINAL {
            @Override
            public Transition choose(Issue issue, List<Transition> transitions) {
                List<String> options = transitions.stream()
                        .map(transition -> transition.getId() + " " + transition.getName())
                        .collect(Collectors.toList());

                Scanner inputReader = new Scanner(System.in);

                System.out.printf("Choose one of the following options%n");
                System.out.printf("  0) cancel%n");
                for (int choice = 1; choice <= options.size(); choice++) {
                    System.out.printf("  %d) %s%n", choice, options.get(choice - 1));
                }

                try {
                    System.out.printf("choose transition ID: ");
                    int choice = inputReader.nextInt();
                    if (choice == 0) {
                        System.out.printf("You canceled your selection%n");
                        return null;
                    }
                    System.out.printf("You selected: %s%n", choice <= options.size() ? options.get(choice - 1) : choice);
                    Transition chosen = transitions.stream()
                            .filter(transition -> transition.getId() == choice)
                            .findFirst()
                            .orElse(transitions.get(choice - 1));

                    return chosen;
                } catch (InputMismatchException exception) {
                    exception.printStackTrace();
                    return null;
                }
            }
        },
        CONFIG_AND_BLOCK {

            private final Configuration configuration = Configuration.loadConfiguration();

            @Override
            public Transition choose(Issue issue, List<Transition> transitions) {
                IssueTypeConfig typeConfig = configuration.configFor(issue.getIssueType().getName());
                Collection<String> phases = typeConfig.getTransitionFlow(BLOCKED_PHASE)
                        .stream()
                        .map(phaseChoice -> Utils.splitToList(phaseChoice, '|'))
                        .map(Collection::stream)
                        .flatMap(Function.identity())
                        .collect(Collectors.toList());

                return transitions.stream()
                        .filter(transition -> {
                            for (String phase : phases) {
                                if (transition.getName().equals(phase)) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .findFirst()
                        .orElse(null);
            }
        };

        public abstract Transition choose(Issue issue, List<Transition> transitions);
    }
}
