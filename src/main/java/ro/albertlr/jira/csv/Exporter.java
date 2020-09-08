/*-
 * #%L
 * jira-cli
 *  
 * Copyright (C) 2019 - 2020 László-Róbert, Albert (robert@albertlr.ro)
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
package ro.albertlr.jira.csv;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.util.CollectionUtils;
import ro.albertlr.jira.Jira;
import ro.albertlr.jira.action.GetE2EsRecursively;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class Exporter {
    public static void issueWithFunctionalArea(Jira jira, Collection<String> issueKeys) throws IOException {
        RecordWithFA.RecordWithFABuilder builder = null;
        Collection<RecordWithFA> records = new ArrayList<>();
        for (String issueKey : issueKeys) {
            Issue issue = jira.loadIssue(issueKey);

            builder = RecordWithFA.builder()
                    .ticketId(issue.getKey())
                    .summary(issue.getSummary());

            for (IssueLink link : issue.getIssueLinks()) {
                link.getIssueLinkType().getName();
            }

            if (GetE2EsRecursively.isReleasableType(issue.getIssueType())) {
                builder.ticketId(issue.getKey());
            } else {
                builder.faId(issue.getKey());
            }

            records.add(builder.build());
        }

        saveMapping(
                "output.csv",
                records.stream()
                        .map(rec -> (Iterable<String>) rec)
                        .collect(Collectors.toList()),
                (Header[]) Header.values()
        );
    }

    public static void exportToCsv(Jira jira, Map<String, Set<Issue>> e2es) throws IOException {
        Record.RecordBuilder builder = null;
        Collection<Record> records = new ArrayList<>();
        for (Map.Entry<String, Set<Issue>> issueToDependsOn : e2es.entrySet()) {
            Issue issue = jira.loadIssue(issueToDependsOn.getKey());

            builder = Record.builder()
                    .summary(issue.getSummary());

            if (GetE2EsRecursively.isReleasableType(issue.getIssueType())) {
                builder.ticketId(issue.getKey());
            } else {
                builder.e2eId(issue.getKey());
            }

            Set<Issue> issues = issueToDependsOn.getValue();
            if (CollectionUtils.isEmpty(issues)) {
                records.add(builder.build());
            } else {
                for (Issue dependsOnE2E : issues) {
                    builder.dependsOnE2EId(dependsOnE2E.getKey());
                    records.add(builder.build());
                }
            }
        }

        saveMapping(
                "output.csv",
                records.stream()
                        .map(rec -> (Iterable<String>) rec)
                        .collect(Collectors.toList()),
                (Header[]) Header.values()
        );
    }

    @Builder
    @Getter
    public static class Record implements Iterable<String> {
        private String ticketId;
        private String e2eId;
        private String summary;
        private String dependsOnE2EId;

        @Override
        public Iterator<String> iterator() {
            return ImmutableList.of
                    (
                            ofNullable(ticketId).orElse(""),
                            ofNullable(e2eId).orElse(""),
                            summary,
                            ofNullable(dependsOnE2EId).orElse("")
                    )
                    .iterator();
        }
    }

    @Builder
    @Getter
    public static class RecordWithFA implements Iterable<String> {
        private String ticketId;
        private String summary;
        private String faId;
        private String faSummary;

        @Override
        public Iterator<String> iterator() {
            return ImmutableList.of
                    (
                            ofNullable(ticketId).orElse(""),
                            summary,
                            ofNullable(faId).orElse(""),
                            faSummary
                    )
                    .iterator();
        }
    }

    public static enum Header {
        Ticket,
        Sub_E2E,
        Summary,
        E2E;
    }

    private static CSVFormat csvFormat() {
        return CSVFormat.DEFAULT
                .withDelimiter(';')
                .withFirstRecordAsHeader();
    }

    public static void saveMapping(String filename, Collection<Iterable<String>> records, Header... headers) throws IOException {
        try (FileWriter out = new FileWriter(filename);
             CSVPrinter printer = csvFormat()
                     .withHeader(headers(headers))
                     .print(out);) {
            for (Iterable<String> record : records) {
                printer.printRecord(record);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String[] headers(Header... headers) {
        String[] headersText = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            headersText[i] = headers[i].name();
        }
        return headersText;
    }


}
