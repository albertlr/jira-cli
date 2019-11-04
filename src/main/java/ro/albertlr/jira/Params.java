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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@UtilityClass
@Slf4j
public class Params {

    public static final String SOURCE_ARG = "source";
    public static final String PROJECT_ARG = "project";
    public static final String ACTION_ARG = "action";
    public static final String TARGET_ARG = "target";
    public static final String LINK_TYPE_ARG = "link-type";

    public static String getParameter(CommandLine cli, String argument, String defaultValue) {
        if (cli.hasOption(argument)) {
            return cli.getOptionValue(argument);
        } else {
            return defaultValue;
        }
    }

    public static String getParameter(CommandLine cli, String argument) {
        return cli.getOptionValue(argument);
    }

    public static CommandLineParser parser() {
        return new DefaultParser();
    }

    public static Options options() {
        Options options = new Options();
        options.addOption(
                Option.builder("a")
                        .required()
                        .longOpt(ACTION_ARG)
                        .desc("Action to do. Can be one of: get, get-e2es, link, clone, move")
                        .hasArg()
                        .argName("action")
                        .build()
        );
        options.addOption(
                Option.builder("r")
                        .optionalArg(true)
                        .longOpt("recursive")
                        .desc("If action is <get-e2es> then can recursively find all the E2Es of E2Es")
                        .build()
        );
        options.addOption(
                Option.builder("t")
                        .longOpt(TARGET_ARG)
                        .desc("Target issue key. ")
                        .hasArg()
                        .argName("ISSUE_ID")
                        .build()
        );
        options.addOption(
                Option.builder()
                        .longOpt(LINK_TYPE_ARG)
                        .desc("Link type to use when action is <link>. Can be <clone>, <depends-on>")
                        .hasArg()
                        .argName("ISSUE_ID")
                        .build()
        );
        options.addOption(
                Option.builder()
                        .longOpt("transition-phase")
                        .desc("The (auto) transition phase to do. Known phases: block,unblock,start,re-open," +
                                "qe-review,block,unblock,reject(review only),accept(review only)")
                        .hasArg()
                        .argName("PHASE")
                        .build()
        );

        options.addOption(
                Option.builder("s")
                        .required()
                        .longOpt(SOURCE_ARG)
                        .desc("Source JIRA key to clone")
                        .hasArg()
                        .argName("ISSUE_ID")
                        .build()
        );
        options.addOption(
                Option.builder("p")
                        .optionalArg(true)
                        .longOpt(PROJECT_ARG)
                        .desc("destination project")
                        .hasArg()
                        .argName("PROJECT_ID")
                        .build()
        );
        options.addOption(
                Option.builder("u")
                        .longOpt("user")
                        .desc("User to authenticate with")
                        .hasArg()
                        .argName("user")
                        .build()
        );
        options.addOption(
                Option.builder("p")
                        .longOpt("password")
                        .desc("User's password to authenticate with")
                        .hasArg()
                        .argName("password")
                        .build()
        );

        options.addOption(
                Option.builder("h")
                        .hasArg(false)
                        .longOpt("help")
                        .desc("Print this help")
                        .build()
        );

        return options;
    }

    public static CommandLine cli(String[] args) {
        return commandLine(parser(), options(), args);
    }

    public static CommandLine commandLine(CommandLineParser commandLineParser, Options options, String[] args) {
        try {
            return commandLineParser.parse(options, args);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
            printUsage();
            return null;
        }
    }

    public static void printUsage() {
        // automatically generate the help statement
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("jira-cli", options());
        System.exit(1);
    }
}
