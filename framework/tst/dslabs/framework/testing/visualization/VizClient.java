/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
 *                    Doug Woos (dwoos@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.visualization;

import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.utils.ClassSearch;
import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public abstract class VizClient {
    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        final Option lab = Option.builder("l").longOpt("lab").required(true)
                                 .type(String.class).argName("LAB").hasArg(true)
                                 .numberOfArgs(1).desc("lab identifier")
                                 .build();
        final Option help = Option.builder("h").longOpt("help").build();
        options.addOption(lab);
        options.addOption(help);
        final CommandLineParser parser = new DefaultParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
            if (line.hasOption(help)) {
                throw new ParseException(null);
            }
        } catch (ParseException e) {
            if (e.getMessage() != null) {
                System.err.println(e.getMessage());
            }
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("-l/--lab LAB <VIZ_ARGUMENTS> [-h/--help]",
                    options);
            return;
        }

        final String labID = line.getOptionValue(lab);

        VizConfig config = null;
        for (var c : ClassSearch.vizConfigs()) {
            Lab l;
            if ((l = c.getAnnotation(Lab.class)) != null &&
                    l.value().equals(labID)) {
                config = c.getDeclaredConstructor().newInstance();
                break;
            }
        }

        if (config == null) {
            throw new RuntimeException(
                    "Could not find viz config for lab " + labID);
        }

        String[] vizArgs = line.getArgs();
        SearchState state;
        try {
            state = config.getInitialState(vizArgs);
        } catch (Exception e) {
            System.err.println("Could not start viz for lab " + labID +
                    " with arguments: " + Arrays.toString(vizArgs));
            String helpString;
            if ((helpString = config.argumentHelpString()) != null) {
                System.err.println("Usage: " + helpString);
            }
            throw e;
        }
        SearchSettings settings = config.defaultSearchSettings();
        new DebuggerWindow(state, settings);
    }
}
