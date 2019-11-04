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
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.IssueLogger;
import ro.albertlr.jira.Jira;
import ro.albertlr.jira.Utils;

import java.util.Collection;

import static ro.albertlr.jira.Action.paramAt;

@Slf4j
public class GetTransitions implements Action<Collection<Transition>> {
    @Override
    public Collection<Transition> execute(Jira jira, String... params) {
        String issueKey = paramAt(params, 0, "issueKey");

        Issue issue = jira.loadIssue(issueKey);

        Iterable<Transition> transitions = jira.loadTransitionsFor(issue);

//        IssueLogger.simpleLog(log, issue, transitions);

        return Lists.newArrayList(transitions);
    }

}
