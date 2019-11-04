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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.BasicProject;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueField;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.Project;
import com.atlassian.jira.rest.client.api.domain.Resolution;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.api.domain.input.CannotTransformValueException;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promise.TryConsumer;
import lombok.extern.slf4j.Slf4j;
import ro.albertlr.jira.Configuration.ActionConfig;
import ro.albertlr.jira.Configuration.IssueTypeConfig;
import ro.albertlr.jira.clone.AddLinks;
import ro.albertlr.jira.clone.CloneConfig;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class Jira implements AutoCloseable {

    public static Jira getInstance() {
        try {
            final String username = loadUser();
            char[] password = loadPassword();
            return new Jira("https://jira.devfactory.com/", username, password);
        } catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }

    private final String user;
    private final char[] password;

    private final JiraRestClientFactory factory;
    private final URI jiraServerUri;

    private volatile transient JiraRestClient jiraRestClient;
    private volatile boolean verbose = false;

    private Configuration configuration;

    private Jira(String jiraServerUrl, String user, char[] password) throws URISyntaxException {
        factory = new AsynchronousJiraRestClientFactory();
        jiraServerUri = new URI(jiraServerUrl);
        this.user = user;
        this.password = password;

        this.configuration = Configuration.loadConfiguration();
    }

    private static String loadAuth() {
        try {
            final String userHome = System.getProperty("user.home");
            File authFile = new File(format("%s/.jira_auth", userHome));
            log.debug("Loading authFile {}", authFile);
            String base64Auth = Files.readFirstLine(authFile, UTF_8);
            return new String(Base64.getDecoder().decode(base64Auth), UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private static String loadUser() {
        return loadAuth().split(":")[0];
    }

    private static char[] loadPassword() {
        return loadAuth().split(":")[1].toCharArray();
    }

    private JiraRestClient restClient() {
        if (jiraRestClient == null) {
            synchronized (this) {
                if (jiraRestClient == null) {
                    log.info("Connecting to JIRA at {} with user {}", jiraServerUri, user);
                    jiraRestClient = factory.createWithBasicHttpAuthentication(jiraServerUri, user, new String(password));
                }
            }
        }
        return jiraRestClient;
    }

    public Project loadProject(String projectKey) {
        try {
            Promise<Project> projectPromise = restClient()
                    .getProjectClient()
                    .getProject(projectKey);
            Project project = projectPromise.get();
            log.info("Loading project {}: {}", projectKey, project.getDescription());
            return project;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(format("failed to load project %s", projectKey), e);
        } catch (ExecutionException e) {
            throw new RuntimeException(format("failed to load project %s", projectKey), e.getCause());
        }
    }

    public IssueRestClient issueClient() {
        return restClient()
                .getIssueClient();
    }

    public Issue loadIssue(String issueKey) {
        try {
            final Promise<Issue> issuePromise = issueClient().getIssue(issueKey);

            Issue issue = issuePromise.get();

            IssueLogger.simpleLog(log, issue);

            return issue;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(format("failed to load issue %s", issueKey), e);
        } catch (ExecutionException e) {
            throw new RuntimeException(format("failed to load issue %s", issueKey), e.getCause());
        }
    }

    public Iterable<Transition> loadTransitionsFor(Issue issue) {
        try {
            Promise<Iterable<Transition>> transisionsPromise = issueClient().getTransitions(issue);
            Iterable<Transition> transitions = transisionsPromise.get();
            return transitions;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        } catch (ExecutionException e) {
            log.error(e.getCause().getMessage(), e.getCause());
        }
        return Collections.emptyList();
    }

    public Promise<Void> transitionIssue(Issue issue, Transition transition) {
        TransitionInput transitionInput = new TransitionInput(transition.getId());
        Promise<Void> result = issueClient()
                .transition(issue, transitionInput);

        return result;
    }

    public Promise<BasicIssue> cloneIssue(Issue source, CloneConfig config) {
        return cloneAndMoveIssue(source, config, source.getProject());
    }

    public Promise<BasicIssue> cloneAndMoveIssue(Issue source, CloneConfig config, BasicProject targetProject) {
        IssueInputBuilder issueBuilder = new IssueInputBuilder(targetProject, source.getIssueType());

        IssueTypeConfig typeConfig = configuration.configFor(source.getIssueType().getName());

        if (typeConfig == null) {
            throw new IllegalArgumentException(
                    format("Cannot clone or move issues of type %s. No configuration found", source.getIssueType().getName())
            );
        }

        Status status = source.getStatus();
        Resolution resolution = source.getResolution();
        issueBuilder.setSummary(source.getSummary())
                .setDescription(source.getDescription())
                .setPriority(source.getPriority());

        if (source.getReporter() != null) {
            User reporter = source.getReporter();
            if (reporter.isActive()) {
                issueBuilder.setReporter(reporter);
            } else {
                log.trace("Set {} as reporter as {} is inactive", user, reporter.getName());
                issueBuilder.setReporterName(user);
            }
        }
        if (source.getAssignee() != null) {
            User assignee = source.getAssignee();
            if (assignee.isActive()) {
                issueBuilder.setAssignee(assignee);
            } else {
                log.trace("Set {} as assignee as {} is inactive", user, assignee.getName());
                issueBuilder.setAssigneeName(user);
            }
        }

        if (config.isCloningAffectedVersions()) {
            Iterable<Version> versions = source.getAffectedVersions();
            if (!Iterables.isEmpty(versions)) {
                issueBuilder.setAffectedVersions(Iterables.limit(versions, 1));
            }
        }
        if (config.isCloningFixVersions()) {
            issueBuilder.setFixVersions(source.getFixVersions());
        }
        if (config.isCloningComponents()) {
            issueBuilder.setComponents(source.getComponents());
        }
        Collection<IssueField> issueFields = Lists.newArrayList(source.getFields());
        if (config.isCloningLabels()) {
            issueBuilder.setFieldValue("labels", Lists.newArrayList(source.getLabels()));
        }

        for (String fieldId : typeConfig.getRequiredFields()) {
            Map<String, Object> asMap = typeConfig.getRequiredFieldOptionsDefault(fieldId);
            log.debug("Setting required field {}: {}", fieldId, asMap);
            issueBuilder.setFieldValue(fieldId, new ComplexIssueInputFieldValue(asMap));
        }

        for (IssueField field : safe(source.getFields())) {
            final String id = field.getId();
            final Object value = field.getValue();
            if (value != null
                    && !typeConfig.getFieldsToNotClone().contains(id)
                    && !typeConfig.getRequiredFields().contains(id)) {
                try {
                    String asString = String.valueOf(value);
                    log.debug("Setting field {}", field);
                    issueBuilder.setFieldValue(id, asString);
                } catch (CannotTransformValueException exception) {
                    log.warn("Could not set field {}", field, exception);
                }
            }
        }

        IssueInput issueInput = issueBuilder.build();

        Promise<BasicIssue> result = issueClient()
                .createIssue(issueInput)
                .then(config.isCloningLinks() ? new AddLinks(this, source, configuration) : noOpConsumer());

//        try {
//            BasicIssue resultingIssue = null;
//            resultingIssue = result.get();
//            log.info("Start linking");
//            new AddLinks(source)
//                    .accept(resultingIssue);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }

//                .then(config.isCloningAttachments() ? new AddAttachments(source) : noOpConsumer())
//                .then(config.isCloningLinks() ? new AddLinks(source) : noOpConsumer())
//                .then(config.isCloningSubtasks() ? new CloneSubtasks(source) : noOpConsumer())
//                .then(
//                        loggingConsumer(
//                                () -> format("Successfully cloned %s", source.getKey()),
//                                () -> format("Failed to clone %s", source.getKey())
//                        )
//                );

        return result;
    }

    public void assignToMe(String key) {
        assignTo(key, this.user);
    }

    public void assignTo(String key, String user) {
        Issue issue = loadIssue(key);
        IssueInput issueInput = IssueInput.createWithFields(
                new FieldInput(
                        IssueFieldId.ASSIGNEE_FIELD,
                        ComplexIssueInputFieldValue.with("name", user)
                )
        );
        issueClient()
                .updateIssue(key, issueInput)
                .claim();
    }

    public void link(String fromKey, String toKey, String linkType) {
        Issue from = loadIssue(fromKey);
        Issue to = loadIssue(toKey);
        link(from, to, linkType);
    }

    public void link(Issue from, Issue to, String linkType) {
        LinkIssuesInput link = new LinkIssuesInput(from.getKey(), to.getKey(), normalizeLinkType(linkType));
        log.info("Link {} to {} as {}", link.getFromIssueKey(), link.getToIssueKey(), link.getLinkType());
        link(link);
    }

    private String normalizeLinkType(String linkType) {
        for (Map.Entry<String, String> type : configuration.getLinkTypes().entrySet()) {
            if (type.getKey().equals(linkType) || type.getValue().equals(linkType)) {
                return type.getValue();
            }
        }
        return linkType;
    }

    public static Promise<BasicIssue> then(Promise<BasicIssue> basicIssue, boolean condition, TryConsumer<BasicIssue> processor) {
        return basicIssue.then(condition ? processor : noOpConsumer());
    }

    public static TryConsumer<BasicIssue> noOpConsumer() {
        return new TryConsumer<BasicIssue>() {
            @Override
            public void fail(@Nonnull Throwable t) {
                log.error("Error occurred", t);
            }

            @Override
            public void accept(BasicIssue basicIssue) {
                log.trace("Processing {}", basicIssue.getKey());
            }
        };
    }

    public static <T> TryConsumer<T> loggingConsumer(Supplier<String> successMessage, Supplier<String> errorMessage) {
        return new TryConsumer<T>() {
            @Override
            public void fail(@Nonnull Throwable t) {
                log.error(errorMessage.get(), t);
            }

            @Override
            public void accept(T input) {
                log.trace(successMessage.get());
            }
        };
    }

    public void link(LinkIssuesInput linkInput) {
        try {
            Promise<Void> response = issueClient()
                    .linkIssue(linkInput);
            Promise<Void> linkedPromise = response
                    .then(
                            loggingConsumer(
                                    () -> format("Link %s to %s as %s was successful", linkInput.getFromIssueKey(), linkInput.getToIssueKey(), linkInput.getLinkType()),
                                    () -> format("Error occurred while linking %s to %s as %s", linkInput.getFromIssueKey(), linkInput.getToIssueKey(), linkInput.getLinkType())
                            )
                    );

            ActionConfig actionConfig = configuration.actionConfigFor(Action.Name.LINK);
            Void linked;
            if (actionConfig != null) {
                long timout = Long.valueOf(actionConfig.getProperties().get("action.link.timeoutMillis"));
                if (timout > 0) {
                    linked = linkedPromise.get(timout, TimeUnit.MILLISECONDS);
                } else {
                    linked = linkedPromise.claim();
                }
            } else {
                linked = linkedPromise.claim();
            }
            log.info(format("Link creation completed from %s to %s as %s", linkInput.getFromIssueKey(), linkInput.getToIssueKey(), linkInput.getLinkType()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Throwables.propagate(e);
        } catch (ExecutionException e) {
            Throwables.propagate(e.getCause());
        } catch (TimeoutException e) {
            Throwables.propagate(e);
        }
    }


    @Override
    public void close() throws Exception {
        if (jiraRestClient != null) {
            synchronized (this) {
                if (jiraRestClient != null) {
                    jiraRestClient.close();
                }
            }
        }
    }


    public static <T> Iterable<T> safe(Iterable<T> iterable) {
        return Optional.ofNullable(iterable).orElse(Collections.emptyList());
    }

}
