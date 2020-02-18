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

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.action.TransitionIssue;
import ro.albertlr.jira.action.AssignTo;
import ro.albertlr.jira.action.AutoTransitionIssue;
import ro.albertlr.jira.action.BlockIssue;
import ro.albertlr.jira.action.Clone;
import ro.albertlr.jira.action.Get;
import ro.albertlr.jira.action.GetE2EsRecursively;
import ro.albertlr.jira.action.GetTransitions;
import ro.albertlr.jira.action.Link;
import ro.albertlr.jira.action.Move;
import ro.albertlr.jira.action.NoOp;
import ro.albertlr.jira.action.UnblockIssue;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

public interface Action<R> {
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Slf4j
    enum Name {
        GET("get", Get::new),
        GET_TRANSITIONS("get-transitions", GetTransitions::new),
        GET_E2ES("get-e2es", GetE2EsRecursively::new),
        LINK("link", Link::new),
        ADVANCE_ISSUE("advance-issue", TransitionIssue::new),
        AUTO_TRANSITION_ISSUE("auto-transition-issue", AutoTransitionIssue::new),
        BLOCK_ISSUE("block-issue", BlockIssue::new),
        UNBLOCK_ISSUE("unblock-issue", UnblockIssue::new),
        CLONE("clone", Clone::new),
        MOVE("move", Move::new),
        ASSIGN_TO("assignTo", AssignTo::new),

        unknown("???", NoOp::new);

        private final String name;
        private final Supplier<Action<?>> factory;

        public <R> R execute(Jira jira, String... params) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            try {
                return ((Action<R>) getFactory().get())
                        .execute(jira, params);
            } finally {
                if (log.isDebugEnabled()) {
                    log.debug("Action {}({}) took {} to execute", name, Arrays.toString(params), stopwatch);
                }
            }
        }

        public static Name from(String name) {
            for (Name n : values()) {
                if (n.name.equals(name)) {
                    return n;
                }
            }
            return unknown;
        }
    }

    default R execute(Jira jira, String key) {
        return execute(jira, new String[]{key});
    }

    R execute(Jira jira, String... params);

    static String paramAt(String[] params, int index, String paramName) {
        checkArgument(params != null, "Params array must not be null");
        checkElementIndex(index, params.length,
                String.format("Parameter %s expected at position %s", paramName, index + 1)
        );

        return params[index];
    }

    static String paramAt(String[] params, int index, String paramName, String defaultValue) {
        try {
            return paramAt(params, index, paramName);
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

}
