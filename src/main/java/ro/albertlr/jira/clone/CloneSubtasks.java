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

import com.atlassian.jira.rest.client.api.domain.Attachment;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Subtask;
import com.atlassian.jira.rest.client.api.domain.input.AttachmentInput;
import io.atlassian.util.concurrent.Promise.TryConsumer;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.stream.StreamSupport;

import static ro.albertlr.jira.Jira.safe;

@Slf4j
public class CloneSubtasks implements TryConsumer<BasicIssue> {
    private final Issue source;

    public CloneSubtasks(Issue source) {
        this.source = source;
    }

    @Override
    public void fail(@Nonnull Throwable t) {
        log.error("Cannot clone subtasks from {}", source.getKey(), t);
    }

    @Override
    public void accept(BasicIssue basicIssue) {
        if (basicIssue instanceof Issue) {
            for (Subtask subtask : safe(source.getSubtasks())) {
                log.info("SKIPPING subtasks {}", subtask);
            }
        }
    }

    AttachmentInput[] toAttachmentInputs(Iterable<Attachment> attachments) {
        return StreamSupport.stream(attachments.spliterator(), false)
                .map(this::toAttachmentInput)
                .toArray(AttachmentInput[]::new);
    }


    AttachmentInput toAttachmentInput(Attachment attachment) {
        try {
            return new AttachmentInput(attachment.getFilename(), attachment.getContentUri().toURL().openStream());
        } catch (IOException e) {
            log.warn("Could not convert attachment {}", attachment.getFilename(), e);
            return null;
        }
    }

}
