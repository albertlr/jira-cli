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

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Jira;

import static ro.albertlr.jira.Action.paramAt;
import static ro.albertlr.jira.Utils.split;

@Slf4j
public class Link implements Action<Void> {
    @Override
    public Void execute(Jira jira, String... params) {
        String jiraSourceKey = paramAt(params, 0, "sourceKey");
        String jiraTargetKey = paramAt(params, 1, "targetKey");
        String linkType = paramAt(params, 2, "linkType");

        Stopwatch stopwatch = Stopwatch.createStarted();

        Iterable<String> sourceKeys = split(jiraSourceKey);
        Iterable<String> targetKeys = split(jiraTargetKey);

        for (String source : sourceKeys) {
            for (String target : targetKeys) {
                jira.link(source, target, linkType);
            }
        }
        log.trace("Linking {} to {} took {}", jiraSourceKey, jiraTargetKey, stopwatch);

        return null;
    }
}
