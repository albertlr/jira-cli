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

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import io.atlassian.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.IssueLogger;
import ro.albertlr.jira.Jira;
import ro.albertlr.jira.clone.CloneConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static ro.albertlr.jira.Utils.split;

@Slf4j
public class Clone implements Action<String> {
    @Override
    public String execute(Jira jira, String... params) {
        String jiraSourceKey = Action.paramAt(params, 0, "sourceKey");

        Iterable<String> sourceKeys = split(jiraSourceKey);

        Collection<String> clonedKeys = new ArrayList<>(5);
        for (String sourceKey : sourceKeys) {
            clonedKeys.add(doCloneIssue(jira, jiraSourceKey));
        }
        return Joiner.on(',').join(clonedKeys);
    }

    private static String doCloneIssue(Jira jira, String issueSourceKey) {
        Issue issue = jira.loadIssue(issueSourceKey);

        log.info("Start cloning {}", issueSourceKey);
        IssueLogger.shortLog(log, issue);

        CloneConfig config = CloneConfig.builder()
                .cloningAttachments(false)
                .cloningSubtasks(false)
                .cloningLinks(true)
                .build();
        Promise<BasicIssue> clonePromise = jira.cloneIssue(issue, config);

        BasicIssue clone = null;
        try {
            clone = clonePromise.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Throwables.propagate(e);
        } catch (ExecutionException e) {
            Throwables.propagate(e);
        }

        log.info("Issue {} cloned to {}", issueSourceKey, clone.getKey());
        IssueLogger.basicLog(log, clone);

        return clone.getKey();
    }
}
