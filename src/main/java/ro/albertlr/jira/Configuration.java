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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Slf4j
public class Configuration {
    public static final String ISSUE_TYPE_IDS = "issueTypeIds";
    public static final String LINK_TYPES = "linkTypes";
    public static final String LINK_TYPE_NAME = "linkType.%s";

    public static final String CONF_ISSUE_TYPE_NAME = "%s.jiraIssueTypeName";
    public static final String CONF_FIELDS_TO_NOT_CLONE = "%s.fieldsToNotClone";
    public static final String CONF_REQUIRED_FIELDS = "%s.requiredFields";
    public static final String CONF_REQUIRED_FIELD_OPTIONS = "%s.requiredField.%s.options";
    public static final String CONF_REQUIRED_FIELD_OPTIONS_DEFAULT = "%s.requiredField.%s.optionsDefault";

    public static Configuration loadConfiguration() {
        Properties properties = new Properties();
        try {
            properties.load(Jira.class.getResourceAsStream("/config.properties"));
        } catch (IOException e) {
            log.error("Could not load configuration from config.properties", e);
        }
        return Configuration.builder()
                .properties(properties)
                .build();
    }

    @Builder
    @Getter
    @ToString
    public static class IssueTypeConfig {
        private final String jiraIssueTypeName;
        private final Set<String> fieldsToNotClone;
        private final Set<String> requiredFields;
        @Singular("requiredFieldOption")
        private final Map<String, Map<String, Object>> requiredFieldOptions;
        @Singular("requiredFieldOptionDefault")
        private final Map<String, Map<String, Object>> requiredFieldOptionsDefault;

        public Map<String, Object> getRequiredFieldOptionsDefault(String requiredField) {
            return requiredFieldOptionsDefault.get(requiredField);
        }
    }

    @Builder
    @Getter
    @ToString
    public static class ActionConfig {
        private Action.Name action;
        @Singular("property")
        private Map<String, String> properties;

        public String getProperty(String property) {
            return getProperty(property, null);
        }

        public String getProperty(String property, String defaultValue) {
            String value = properties.get(property);
            return Optional.ofNullable(value)
                    .orElse(defaultValue);
        }
    }

    private Properties properties;

    private Set<String> actions;
    private Map<Action.Name, ActionConfig> actionConfigs;

    private Set<String> knownLinkTypes;
    private Map<String, String> linkTypes;

    private Set<String> issueTypeIds;
    private Map<String, IssueTypeConfig> issueTypeConfigs;

    @Builder
    private Configuration(Properties properties) {
        this.properties = properties;
        load(this.properties);
    }

    public void load(Properties config) {
        loadKnownLinkTypeConfig(config);
        loadActionsConfig(config);
        loadIssueTypeConfigs(config);
    }

    private void loadIssueTypeConfigs(Properties config) {
        this.issueTypeIds = toSet(config.getProperty(ISSUE_TYPE_IDS));

        Map<String, IssueTypeConfig> collector = Maps.newHashMap();

        for (String issueTypeId : this.issueTypeIds) {
            IssueTypeConfig.IssueTypeConfigBuilder configBuilder = IssueTypeConfig.builder()
                    .jiraIssueTypeName(config.getProperty(keyOf(CONF_ISSUE_TYPE_NAME, issueTypeId)))
                    .fieldsToNotClone(toSet(config.getProperty(keyOf(CONF_FIELDS_TO_NOT_CLONE, issueTypeId))));

            Set<String> requiredFields = toSet(config.getProperty(keyOf(CONF_REQUIRED_FIELDS, issueTypeId)));
            configBuilder.requiredFields(requiredFields);
            for (String requiredField : requiredFields) {
                Set<Entry<String, String>> options = toEntrySet(config.getProperty(keyOf(CONF_REQUIRED_FIELD_OPTIONS, issueTypeId, requiredField)));
                configBuilder.requiredFieldOption(
                        requiredField,
                        options.stream()
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
                );

                Set<Entry<String, String>> optionsDefault = toEntrySet(config.getProperty(keyOf(CONF_REQUIRED_FIELD_OPTIONS_DEFAULT, issueTypeId, requiredField)));
                configBuilder.requiredFieldOptionDefault(
                        requiredField,
                        optionsDefault.stream()
                                .map(e -> Maps.<String, Object>immutableEntry(e.getKey(), e.getValue()))
                                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
                );
            }

            IssueTypeConfig issueTypeConfig = configBuilder.build();
            log.trace("issue type config for {}: {}", issueTypeId, issueTypeConfig);
            collector.put(issueTypeId, issueTypeConfig);
        }

        issueTypeConfigs = ImmutableMap.copyOf(collector);
    }

    private void loadActionsConfig(Properties config) {
        this.actions = toSet(config.getProperty("actions"));
        Map<Action.Name, ActionConfig> actionConfigs = new HashMap<>();

        for (String action : actions) {
            final String actionPrefix = keyOf("action.%s.", action);
            ActionConfig.ActionConfigBuilder actionConfigBuilder = ActionConfig.builder();
            actionConfigBuilder.action(Action.Name.from(action));

            if (actionConfigBuilder.action.equals(Action.Name.unknown)) {
                log.warn("Unable to process action config for action {}", action);
                continue;
            }

            for (String propertyName : config.stringPropertyNames()) {
                if (propertyName.startsWith(actionPrefix)) {
                    actionConfigBuilder.property(propertyName, config.getProperty(propertyName));
                }
            }

            ActionConfig actionConfig = actionConfigBuilder.build();
            actionConfigs.put(actionConfig.action, actionConfig);
        }

        this.actionConfigs = new EnumMap<Action.Name, ActionConfig>(actionConfigs);
    }

    private void loadKnownLinkTypeConfig(Properties config) {
        this.knownLinkTypes = toSet(config.getProperty(LINK_TYPES));

        Map<String, String> linkTypes = new HashMap<>();
        for (String linkType : knownLinkTypes) {
            linkTypes.put(linkType, config.getProperty(keyOf(LINK_TYPE_NAME, linkType)));
        }
        this.linkTypes = ImmutableMap.copyOf(linkTypes);
    }

    public ActionConfig actionConfigFor(Action.Name action) {
        return this.actionConfigs.get(action);
    }

    public IssueTypeConfig configFor(String issueType) {
        if (issueTypeConfigs.containsKey(issueType)) {
            return issueTypeConfigs.get(issueType);
        }
        return issueTypeConfigs.values().stream()
                .filter(config -> config.jiraIssueTypeName.equals(issueType))
                .findFirst()
                .orElse(null);
    }

    static Set<String> toSet(String commaSeparatedText) {
        if (commaSeparatedText == null) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(
                Splitter.on(',')
                        .omitEmptyStrings()
                        .trimResults()
                        .split(commaSeparatedText)
        );
    }

    static Set<Map.Entry<String, String>> toEntrySet(String commaSeparatedText) {
        return ImmutableSet.copyOf(
                Splitter.on(',')
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(commaSeparatedText)
                        .stream()
                        .map(Configuration::toEntry)
                        .collect(Collectors.toSet())
        );
    }

    static Map.Entry<String, String> toEntry(String colonSeparatedText) {
        List<String> pair = Splitter.on(':')
                .omitEmptyStrings()
                .trimResults()
                .limit(2)
                .splitToList(colonSeparatedText);
        return Maps.immutableEntry(pair.get(0), pair.get(1));
    }

    static String keyOf(String keyTemplate, String issueTypeId) {
        return String.format(keyTemplate, issueTypeId);
    }

    static String keyOf(String keyTemplate, String issueTypeId, String field) {
        return String.format(keyTemplate, issueTypeId, field);
    }


}
