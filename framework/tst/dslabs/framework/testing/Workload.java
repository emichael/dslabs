/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
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

package dslabs.framework.testing;

import com.google.common.collect.Lists;
import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.framework.testing.utils.SerializableFunction;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public abstract class Workload implements Serializable {
    private static final boolean DEFAULT_DO_REPLACEMENTS = true;

    /**
     * @param clientAddress
     *         the address of the client executing the operations
     * @return a pair of the next operation and result, in that order
     *
     * @throws UnsupportedOperationException
     *         if this operations does not contain results
     */
    public abstract Pair<Command, Result> nextCommandAndResult(
            Address clientAddress);

    public Command nextCommand(Address clientAddress) {
        return nextCommandAndResult(clientAddress).getLeft();
    }

    public abstract boolean hasNext();

    public abstract boolean hasResults();

    public abstract void add(Command command);

    public abstract void add(Command command, Result result);

    public void add(String command) {
        throw new UnsupportedOperationException();
    }

    public void add(String command, String result) {
        throw new UnsupportedOperationException();
    }

    public int millisBetweenRequests() {
        return 0;
    }

    public final boolean isRateLimited() {
        return millisBetweenRequests() > 0;
    }

    public abstract void reset();

    /**
     * Returns the number of commands in the workload. If the workload is
     * infinite, the return value of this function is undefined.
     *
     * @return the number of commands in the workload
     */
    public abstract int size();


    public abstract boolean infinite();

    /**
     * Takes a command string and a result string and makes replacements.
     *
     * <p>Turns %r into random string of 8 characters, %rN into random string
     * of N characters, %n into a random number from 1 to 100, %nN into a random
     * number from 1 to N, %a into a.toString(), %i into value of i, %i-1 into
     * one less than the value of i, and %i+1 into one greater than the value of
     * i.
     *
     * <p>The same random strings will be used in both the operation and result
     * as long as the exact same identifiers are used.
     *
     * TODO: add javadoc to workload, try to import the javadoc for this
     * method?
     *
     * @param command
     *         the command string with patterns to replace
     * @param result
     *         the result string with patterns to replace
     * @param i
     *         the value of i to use in replacement
     * @return the string with patterns replaced
     */
    protected static Pair<String, String> doReplacements(String command,
                                                         String result,
                                                         Address a, int i) {
        if (command == null) {
            return new ImmutablePair<>(null, null);
        }

        Pair<String, Map<String, List<String>>> newoperation =
                doReplacements(command, a, i, null);

        if (result == null) {
            return new ImmutablePair<>(newoperation.getLeft(), null);
        }

        Pair<String, Map<String, List<String>>> newResult =
                doReplacements(result, a, i, newoperation.getRight());

        return new ImmutablePair<>(newoperation.getLeft(), newResult.getLeft());
    }

    private static Pair<String, Map<String, List<String>>> doReplacements(
            String s, Address a, int i, Map<String, List<String>> randomness) {
        boolean useRandomness = randomness != null;
        if (!useRandomness) {
            randomness = new HashMap<>();
        }

        Random rand = new Random();

        Pattern token = Pattern.compile("%(?:r(\\d*)|n(\\d*)|i(?:-1|\\+1)?|a)");

        StringBuffer ret = new StringBuffer();
        Matcher matcher = token.matcher(s);

        while (matcher.find()) {
            String fullMatch = matcher.group();
            switch (fullMatch.charAt(1)) {
                case 'r':
                    String randomString = null;
                    if (useRandomness && randomness.containsKey(fullMatch) &&
                            randomness.get(fullMatch).size() > 0) {
                        randomString = randomness.get(fullMatch).remove(0);
                    }

                    if (randomString == null) {
                        int numBytes;
                        if (!matcher.group(1).isEmpty()) {
                            numBytes = Integer.parseInt(matcher.group(1));
                        } else {
                            numBytes = 8;
                        }
                        randomString =
                                RandomStringUtils.randomAlphanumeric(numBytes);
                    }

                    if (!useRandomness) {
                        if (!randomness.containsKey(fullMatch)) {
                            randomness.put(fullMatch, new LinkedList<>());
                        }
                        randomness.get(fullMatch).add(randomString);
                    }

                    matcher.appendReplacement(ret, randomString);
                    break;

                case 'n':
                    randomString = null;
                    if (useRandomness && randomness.containsKey(fullMatch) &&
                            randomness.get(fullMatch).size() > 0) {
                        randomString = randomness.get(fullMatch).remove(0);
                    }

                    if (randomString == null) {
                        int upperBound;
                        if (matcher.group(2) != null &&
                                !matcher.group(2).isEmpty()) {
                            upperBound = Integer.parseInt(matcher.group(2));
                        } else {
                            upperBound = 100;
                        }
                        randomString =
                                Integer.toString(rand.nextInt(upperBound) + 1);
                    }

                    if (!useRandomness) {
                        if (!randomness.containsKey(fullMatch)) {
                            randomness.put(fullMatch, new LinkedList<>());
                        }
                        randomness.get(fullMatch).add(randomString);
                    }

                    matcher.appendReplacement(ret, randomString);
                    break;

                case 'i':
                    if (fullMatch.equals("%i-1")) {
                        matcher.appendReplacement(ret, Integer.toString(i - 1));
                    } else if (fullMatch.equals("%i+1")) {
                        matcher.appendReplacement(ret, Integer.toString(i + 1));
                    } else {
                        matcher.appendReplacement(ret, Integer.toString(i));
                    }
                    break;

                case 'a':
                    matcher.appendReplacement(ret, a.toString());
                    break;
            }
        }
        matcher.appendTail(ret);

        if (useRandomness) {
            return new ImmutablePair<>(ret.toString(), null);
        } else {
            return new ImmutablePair<>(ret.toString(), randomness);
        }
    }

    // TODO: rename class?
    private static class StandardWorkload extends Workload {
        private final List<Command> commands;
        private final List<Result> results;

        private final List<String> commandStrings;
        private final List<String> resultStrings;

        // TODO: annotate immutable instead of type "SerializableFunction"?
        private final SerializableFunction<Pair<String, String>, Pair<Command, Result>>
                parser;

        private final int numTimes;
        private final boolean finite, doReplacements;

        private int i = 0;

        private StandardWorkload(List<Command> commands, List<Result> results,
                                 List<String> commandStrings,
                                 List<String> resultStrings,
                                 SerializableFunction<Pair<String, String>, Pair<Command, Result>> parser,
                                 int numTimes, boolean finite,
                                 boolean doReplacements) {
            // TODO: restructure and allow either commands or commandStrings when both empty
            if (!finite && ((commands != null && commands.size() == 0) ||
                    (commandStrings != null && commandStrings.size() == 0))) {
                throw new IllegalArgumentException(
                        "Cannot create empty infinite workload");
            }

            if (commands != null) {
                if (commandStrings != null || resultStrings != null) {
                    throw new IllegalArgumentException(
                            "Cannot create workload with commands and command strings");
                }

                if (results != null && commands.size() != results.size()) {
                    throw new IllegalArgumentException(
                            "Commands size and results size must match");
                }

                this.commands = new ArrayList<>(commands);
                this.results = results == null ? new ArrayList<>() :
                        new ArrayList<>(results);
                this.commandStrings = null;
                this.resultStrings = null;
                this.parser = null;

            } else if (commandStrings != null) {
                if (results != null) {
                    throw new IllegalArgumentException(
                            "Cannot create workload with commands and command strings");
                }

                if (parser == null) {
                    throw new IllegalArgumentException(
                            "Must have parser for command and result strings");
                }

                if (resultStrings != null &&
                        commandStrings.size() != resultStrings.size()) {
                    throw new IllegalArgumentException(
                            "Commands size and results size must match");
                }

                this.commands = null;
                this.results = null;
                this.commandStrings = new ArrayList<>(commandStrings);
                this.resultStrings = resultStrings == null ? new ArrayList<>() :
                        new ArrayList<>(resultStrings);
                this.parser = parser;

            } else {
                throw new IllegalArgumentException(
                        "Must have commands or command strings");
            }

            // TODO: add more checks here for numTimes and finite

            this.finite = finite;
            this.doReplacements = doReplacements;
            this.numTimes = finite ? (numTimes < 1 ? 1 : numTimes) : 1;
        }

        private int listSize() {
            return commands != null ? commands.size() : commandStrings.size();
        }

        private Pair<Command, Result> nextPairInternal(Address a) {
            if (!hasNext()) {
                throw new RuntimeException("Workload finished.");
            }

            int index = i % listSize();

            Command command;
            Result result = null;

            if (commands != null) {
                command = commands.get(index);
                if (hasResults()) {
                    result = results.get(index);
                }
            } else {
                String commandString = commandStrings.get(index);
                String resultString = null;
                if (hasResults()) {
                    resultString = resultStrings.get(index);
                }
                if (doReplacements) {
                    Pair<String, String> replaced =
                            doReplacements(commandString, resultString, a,
                                    i + 1);
                    commandString = replaced.getLeft();
                    resultString = replaced.getRight();
                }
                Pair<Command, Result> parsed = parser.apply(
                        new ImmutablePair<>(commandString, resultString));
                command = parsed.getLeft();
                result = parsed.getRight();
            }

            i++;

            // TODO: clone before returning?

            return new ImmutablePair<>(command, result);
        }

        @Override
        public Pair<Command, Result> nextCommandAndResult(
                Address clientAddress) {
            if (!hasResults()) {
                throw new UnsupportedOperationException(
                        "Workload doesn't contain results");
            }

            return nextPairInternal(clientAddress);
        }

        @Override
        public Command nextCommand(Address clientAddress) {
            return nextPairInternal(clientAddress).getLeft();
        }

        @Override
        public boolean hasNext() {
            return !finite || i < listSize() * numTimes;
        }

        @Override
        public boolean hasResults() {
            if (commands != null) {
                return commands.size() == results.size();
            } else {
                return commandStrings.size() == resultStrings.size();
            }
        }

        @Override
        public void add(Command command) {
            if (commands == null) {
                throw new UnsupportedOperationException(
                        "Workload has command strings");
            }

            if (commands.size() > 0 && hasResults()) {
                throw new UnsupportedOperationException("Workload has results");
            }

            if (!finite || numTimes > 1) {
                throw new UnsupportedOperationException(
                        "Cannot add to an infinite or repeating workload");
            }

            commands.add(command);
        }

        @Override
        public void add(Command command, Result result) {
            if (commands == null) {
                throw new UnsupportedOperationException(
                        "Workload has command strings");
            }

            if (!hasResults()) {
                throw new UnsupportedOperationException(
                        "Workload does not have results");
            }

            if (!finite || numTimes > 1) {
                throw new UnsupportedOperationException(
                        "Cannot add to an infinite or repeating workload");
            }

            commands.add(command);
            results.add(result);
        }

        @Override
        public void add(String command) {
            if (commandStrings == null) {
                throw new UnsupportedOperationException(
                        "Workload doesn't have command strings");
            }

            if (commandStrings.size() > 0 && hasResults()) {
                throw new UnsupportedOperationException("Workload has results");
            }

            if (!finite || numTimes > 1) {
                throw new UnsupportedOperationException(
                        "Cannot add to an infinite or repeating workload");
            }

            commandStrings.add(command);
        }

        @Override
        public void add(String command, String result) {
            if (commandStrings == null) {
                throw new UnsupportedOperationException(
                        "Workload doesn't have command strings");
            }

            if (!hasResults()) {
                throw new UnsupportedOperationException(
                        "Workload does not have results");
            }

            if (!finite || numTimes > 1) {
                throw new UnsupportedOperationException(
                        "Cannot add to an infinite or repeating workload");
            }

            commandStrings.add(command);
            resultStrings.add(result);
        }

        @Override
        public void reset() {
            i = 0;
        }

        @Override
        public int size() {
            return finite ? listSize() * numTimes : -1;
        }

        @Override
        public boolean infinite() {
            return !finite;
        }
    }

    @Setter
    public static class WorkloadBuilder {
        private List<Command> commands;
        private List<Result> results;

        private List<String> commandStrings;
        private List<String> resultStrings;

        private SerializableFunction<Pair<String, String>, Pair<Command, Result>>
                parser;

        private boolean finite = true;
        private boolean doReplacements = DEFAULT_DO_REPLACEMENTS;
        private int numTimes = 1;

        private WorkloadBuilder() {
        }

        public WorkloadBuilder commands(List<Command> commands) {
            this.commands = commands;
            return this;
        }

        public WorkloadBuilder commands(Command... commands) {
            if (commands == null) {
                this.commands = Lists.newArrayList();
            } else {
                this.commands = Lists.newArrayList(commands);
            }
            return this;
        }

        public WorkloadBuilder commandStrings(List<String> commandStrings) {
            this.commandStrings = commandStrings;
            return this;
        }

        public WorkloadBuilder commandStrings(String... commandStrings) {
            if (commandStrings == null) {
                this.commandStrings = Lists.newArrayList();
            } else {
                this.commandStrings = Lists.newArrayList(commandStrings);
            }
            return this;
        }

        public WorkloadBuilder results(List<Result> results) {
            this.results = results;
            return this;
        }

        public WorkloadBuilder results(Result... results) {
            if (results == null) {
                this.results = Lists.newArrayList();
            } else {
                this.results = Lists.newArrayList(results);
            }
            return this;
        }

        public WorkloadBuilder resultStrings(List<String> resultStrings) {
            this.resultStrings = resultStrings;
            return this;
        }

        public WorkloadBuilder resultStrings(String... resultStrings) {
            if (resultStrings == null) {
                this.resultStrings = Lists.newArrayList();
            } else {
                this.resultStrings = Lists.newArrayList(resultStrings);
            }
            return this;
        }

        public WorkloadBuilder infinite(boolean infinite) {
            finite = !infinite;
            return this;
        }

        public Workload build() {
            return new StandardWorkload(commands, results, commandStrings,
                    resultStrings, parser, numTimes, finite, doReplacements);
        }
    }

    public static WorkloadBuilder builder() {
        return new WorkloadBuilder();
    }

    public static Workload emptyWorkload() {
        return builder().commands().build();
    }

    public static Workload workload(Command... commands) {
        return builder().commands(commands).build();
    }

    public static Workload workload(List<Command> commands) {
        return builder().commands(commands).build();
    }

    public static Workload workload(List<Command> commands,
                                    List<Result> results) {
        return builder().commands(commands).results(results).build();
    }
}
