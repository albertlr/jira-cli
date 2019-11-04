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

import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Configuration;
import ro.albertlr.jira.Jira;

@Slf4j
public class UnblockIssue implements Action<Void> {

    public static final String UNBLOCKED_PHASE = "unblock";
    private final Configuration configuration = Configuration.loadConfiguration();

    @Override
    public Void execute(Jira jira, String... params) {
        String issueKeys = Action.paramAt(params, 0, "issueKey");

        Name.AUTO_TRANSITION_ISSUE
                .execute(jira, issueKeys, UNBLOCKED_PHASE);

        return null;
    }

}
