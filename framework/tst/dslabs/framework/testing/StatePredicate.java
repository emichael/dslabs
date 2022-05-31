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
import dslabs.framework.Message;
import dslabs.framework.Result;
import dslabs.framework.testing.utils.SerializableFunction;
import dslabs.framework.testing.utils.SerializablePredicate;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


@RequiredArgsConstructor
public class StatePredicate implements Serializable {
    public static final Pair<Boolean, String> TRUE_NO_MESSAGE =
            new ImmutablePair<>(true, null);
    public static final Pair<Boolean, String> FALSE_NO_MESSAGE =
            new ImmutablePair<>(false, null);

    /* Predicates */

    public static final StatePredicate RESULTS_OK =
            statePredicateWithMessage("Clients got expected results", s -> {
                for (ClientWorker c : s.clientWorkers()) {
                    if (!c.resultsOk()) {
                        Pair<Result, Result> p = c.expectedAndReceived();
                        if (p == null) {
                            return new ImmutablePair<>(false,
                                    String.format("%s got an unexpected result",
                                            c.address()));
                        } else {
                            return new ImmutablePair<>(false,
                                    String.format("%s got %s, expected %s",
                                            c.address(), p.getRight(),
                                            p.getLeft()));
                        }
                    }
                }
                return TRUE_NO_MESSAGE;
            });

    public static final StatePredicate NONE_DECIDED =
            resultPredicate("No results returned", rs -> rs.size() <= 0,
                    Quantifier.ALL);

    public static final StatePredicate CLIENTS_DONE =
            statePredicate("All clients' workloads finished",
                    AbstractState::clientWorkersDone);

    public static StatePredicate clientDone(Address clientWorkerAddress) {
        return statePredicate(
                String.format("%s's workload finished", clientWorkerAddress),
                s -> s.clientWorker(clientWorkerAddress).done());
    }

    public static StatePredicate clientHasResults(Address clientWorkerAddress,
                                                  int numResults) {
        return statePredicate(
                String.format("%s received %s results", clientWorkerAddress,
                        numResults),
                s -> s.clientWorker(clientWorkerAddress).results().size() ==
                        numResults);
    }

    public static final StatePredicate ALL_RESULTS_SAME =
            resultsPredicateWithMessage("All clients' results are the same",
                    rs -> {
                        List<List<Result>> distinctResults =
                                rs.stream().distinct().limit(2)
                                  .collect(Collectors.toList());
                        if (distinctResults.size() > 1) {
                            return new ImmutablePair<>(false,
                                    String.format("%s does not match %s",
                                            distinctResults.get(0),
                                            distinctResults.get(1)));
                        }
                        return TRUE_NO_MESSAGE;
                    });

    private static StatePredicate resultsMatch(List<Result> expectedResults,
                                               Quantifier quantifier) {
        final List<Result> er = new ArrayList<>(expectedResults);

        String name = null;
        switch (quantifier) {
            case ALL:
                name = String.format("All clients' results prefix of: %s", er);
                break;
            case ANY:
                name = String.format("Any client's results prefix of: %s", er);
                break;
        }

        return resultPredicate(name,
                r -> r.size() <= er.size() && r.equals(er.subList(0, r.size())),
                quantifier);
    }

    public static StatePredicate allResultsMatch(List<Result> expectedResults) {
        return resultsMatch(expectedResults, Quantifier.ALL);
    }

    public static StatePredicate allResultsMatch(Result... expectedResults) {
        return allResultsMatch(Lists.newArrayList(expectedResults));
    }

    public static StatePredicate anyResultsMatch(List<Result> expectedResults) {
        return resultsMatch(expectedResults, Quantifier.ANY);
    }

    public static StatePredicate anyResultsMatch(Result... expectedResults) {
        return anyResultsMatch(Lists.newArrayList(expectedResults));
    }

    public static StatePredicate containsEnvelopMatching(String name,
                                                         SerializablePredicate<MessageEnvelope> predicate) {
        return statePredicate(
                String.format("Network contains message satisfying: %s", name),
                s -> StreamSupport.stream(s.network().spliterator(), false)
                                  .anyMatch(predicate));
    }

    public static StatePredicate containsMessageMatching(String name,
                                                         SerializablePredicate<Message> predicate) {
        return containsEnvelopMatching(name, e -> predicate.test(e.message()));
    }

    public static StatePredicate resultsHaveType(Address clientAddress,
                                                 Class<?> c) {
        return resultPredicate(
                String.format("All results for %s have type %s", clientAddress,
                        c.getSimpleName()), clientAddress, rs -> rs.stream()
                                                                   .allMatch(
                                                                           r -> c.isAssignableFrom(
                                                                                   r.getClass())));
    }

    /* Class Implementation */

    @Getter private final String name;
    @NonNull private final SerializableFunction<AbstractState, Pair<Boolean, String>>
            predicate;

    private static <T> SerializableFunction<T, Pair<Boolean, String>> addNullMessage(
            String name, SerializablePredicate<T> predicate) {
        return t -> new ImmutablePair<>(predicate.test(t), null);
    }

    public static StatePredicate statePredicateWithMessage(String name,
                                                           SerializableFunction<AbstractState, Pair<Boolean, String>> predicateWithMessage) {
        return new StatePredicate(name, predicateWithMessage);
    }

    public static StatePredicate statePredicate(String name,
                                                SerializablePredicate<AbstractState> predicate) {
        return statePredicateWithMessage(name, addNullMessage(name, predicate));
    }

    public static StatePredicate resultsPredicateWithMessage(String name,
                                                             SerializableFunction<Collection<List<Result>>, Pair<Boolean, String>> predicate) {
        return statePredicateWithMessage(name,
                s -> predicate.apply(s.results().values()));
    }

    public static StatePredicate resultsPredicate(String name,
                                                  SerializablePredicate<Collection<List<Result>>> predicate) {
        return resultsPredicateWithMessage(name,
                addNullMessage(name, predicate));
    }

    public enum Quantifier {
        ANY, ALL
    }

    public static StatePredicate resultPredicateWithMessage(String name,
                                                            SerializableFunction<List<Result>, Pair<Boolean, String>> predicate,
                                                            Quantifier quantifier) {
        return resultsPredicateWithMessage(name, rs -> {
            for (List<Result> r : rs) {
                Pair<Boolean, String> ret = predicate.apply(r);
                switch (quantifier) {
                    case ALL:
                        if (!ret.getLeft()) {
                            return ret;
                        }
                        break;

                    case ANY:
                        if (ret.getLeft()) {
                            return ret;
                        }
                        break;
                }
            }

            // TODO: add messages here??
            switch (quantifier) {
                case ALL:
                    return TRUE_NO_MESSAGE;

                case ANY:
                    return FALSE_NO_MESSAGE;

                default:
                    throw new IllegalArgumentException();
            }
        });
    }

    public static StatePredicate resultPredicate(String name,
                                                 SerializablePredicate<List<Result>> predicate,
                                                 Quantifier quantifier) {
        return resultPredicateWithMessage(name, addNullMessage(name, predicate),
                quantifier);
    }

    public static StatePredicate resultPredicateWithMessage(String name,
                                                            Address clientWorkerAddress,
                                                            SerializableFunction<List<Result>, Pair<Boolean, String>> predicate) {
        return statePredicateWithMessage(name, s -> predicate.apply(
                s.clientWorker(clientWorkerAddress).results()));
    }

    public static StatePredicate resultPredicate(String name,
                                                 Address clientWorkerAddress,
                                                 SerializablePredicate<List<Result>> predicate) {
        return resultPredicateWithMessage(name, clientWorkerAddress,
                addNullMessage(name, predicate));
    }

    /**
     * A class representing the result of evaluating a StatePredicate on a
     * state. Because StatePredicates can call user code, they can throw
     * exceptions.
     *
     * Invariant: this.exceptionThrown() == (this.value() == null)
     */
    public static class PredicateResult {
        @Getter private final StatePredicate predicate;
        private final Throwable throwable;
        private final Boolean value;
        /**
         * The detail string returned by the predicate.
         */
        @Getter private final String detail;

        PredicateResult(@NonNull StatePredicate predicate,
                        @NonNull Throwable throwable) {
            this.predicate = predicate;
            this.throwable = throwable;
            value = null;
            detail = null;
        }

        PredicateResult(@NonNull StatePredicate predicate, boolean value,
                        String detail) {
            this.predicate = predicate;
            this.value = value;
            this.detail = detail;
            throwable = null;
        }

        /**
         * Whether an exception (or error) was thrown during predicate
         * evaluation.
         *
         * @return whether an exception was thrown
         */
        public boolean exceptionThrown() {
            return throwable != null;
        }

        /**
         * The value returned by the predicate, or {@code null} if an exception
         * was thrown during predicate evaluation
         *
         * @return the value or {@code null{}}
         */
        public Boolean value() {
            return value;
        }

        /**
         * A full error message explaining this result.
         *
         * @return the message
         */
        public String errorMessage() {
            final StringBuilder sb = new StringBuilder();
            if (throwable != null) {
                sb.append("Exception thrown while evaluating \"");
                if (predicate.name.length() > 100) {
                    sb.append(predicate.name, 0, 100);
                    sb.append("...");
                } else {
                    sb.append(predicate.name);
                }
                sb.append("\"");
                sb.append("\n");
                final StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            } else {
                assert value != null;

                if (value) {
                    sb.append("State matches \"");
                } else {
                    sb.append("State violates \"");
                }
                if (predicate.name.length() > 100) {
                    sb.append(predicate.name, 0, 100);
                    sb.append("...");
                } else {
                    sb.append(predicate.name);
                }
                sb.append("\"");

                if (detail != null) {
                    sb.append("\nError info: ").append(detail);
                }
            }
            return sb.toString();
        }
    }

    /**
     * Evaluate the predicate on a state and return the result.
     *
     * @param state
     *         the state to evaluate the predicate on
     * @return the PredicateResult
     */
    public final PredicateResult test(AbstractState state) {
        Pair<Boolean, String> res;
        try {
            res = predicate.apply(state);
        } catch (Throwable t) {
            return new PredicateResult(this, t);
        }
        return new PredicateResult(this, res.getLeft(), res.getRight());
    }

    /**
     * Evaluate the predicate on a state and return the result only if the value
     * returned by the predicate <b>is not</b> {@code normalValue}; if the value
     * is {@code normalValue}, this method does not allocate a
     * {@link PredicateResult} and instead returns {@code null}. If an exception
     * is thrown during predicate evaluation, a result is always returned.
     *
     * @param state
     *         the state to evaluate the predicate on
     * @param normalValue
     *         the normal value of the predicate
     * @return the PredicateResult or {@code null}
     */
    public final PredicateResult test(AbstractState state,
                                      boolean normalValue) {
        Pair<Boolean, String> res;
        try {
            res = predicate.apply(state);
        } catch (Throwable t) {
            return new PredicateResult(this, t);
        }
        boolean value = res.getLeft();
        if (value != normalValue) {
            return new PredicateResult(this, value, res.getRight());
        }
        return null;
    }

    public StatePredicate negate() {
        String newName;
        if (name.startsWith("¬(") && name.endsWith(")")) {
            newName = name.substring(2, name.length() - 1);
        } else {
            newName = String.format("¬(%s)", name);
        }
        return statePredicateWithMessage(newName, s -> {
            Pair<Boolean, String> ret = predicate.apply(s);
            return new ImmutablePair<>(!ret.getLeft(), ret.getRight());
        });
    }

    public StatePredicate and(@NonNull StatePredicate other) {
        return statePredicateWithMessage(
                String.format("(%s) ∧ (%s)", this.name, other.name), s -> {
                    Pair<Boolean, String> ret1 = predicate.apply(s);
                    Pair<Boolean, String> ret2;
                    if (!ret1.getLeft()) {
                        return ret1;
                    } else if (!(ret2 = other.predicate.apply(s)).getLeft()) {
                        return ret2;
                    } else {
                        return Pair.of(true,
                                String.format("(%s) and (%s)", ret1.getRight(),
                                        ret2.getRight()));
                    }
                });
    }

    public StatePredicate or(@NonNull StatePredicate other) {
        return statePredicateWithMessage(
                String.format("(%s) ∨ (%s)", this.name, other.name), s -> {
                    Pair<Boolean, String> ret1 = predicate.apply(s);
                    Pair<Boolean, String> ret2;
                    if (ret1.getLeft()) {
                        return ret1;
                    } else if ((ret2 = other.predicate.apply(s)).getLeft()) {
                        return ret2;
                    } else {
                        return Pair.of(false,
                                String.format("(%s) or (%s)", ret1.getRight(),
                                        ret2.getRight()));
                    }
                });
    }

    public StatePredicate implies(StatePredicate other) {
        return statePredicateWithMessage(
                String.format("(%s) → (%s)", this.name, other.name),
                this.negate().or(other).predicate);
    }

    @Override
    public String toString() {
        return name;
    }
}
