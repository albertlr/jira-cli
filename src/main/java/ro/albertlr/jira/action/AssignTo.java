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

import com.google.common.base.Splitter;
import ro.albertlr.jira.Action;
import ro.albertlr.jira.Jira;

import java.util.function.BiFunction;

public class AssignTo implements Action<Void> {
    @Override
    public Void execute(Jira jira, String... params) {
        String issueKeys = Action.paramAt(params, 0, "issueKey");
        String username = Action.paramAt(params, 1, "assign-to", "@me");

        Iterable<String> sourceKeys = Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .split(issueKeys);


        BiFunction<String, String, Void> assignTo = null;
        if ("@me".equals(username)) {
            assignTo = (key, ignore) -> {
                jira.assignToMe(key);
                return null;
            };
        } else {
            assignTo = (key, user) -> {
                jira.assignTo(key, user);
                return null;
            };
        }

        for (String sourceKey : sourceKeys) {
            assignTo.apply(sourceKey, username);
        }

        return null;
    }
}
