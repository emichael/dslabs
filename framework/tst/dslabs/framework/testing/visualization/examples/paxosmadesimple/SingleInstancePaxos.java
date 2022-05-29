/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
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

package dslabs.framework.testing.visualization.examples.paxosmadesimple;


import com.google.common.collect.Streams;
import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.VizIgnore;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


@Lab("PaxosMadeSimple")
public class SingleInstancePaxos extends VizConfig {
    @Override
    public String argumentHelpString() {
        return "NUM_PROPOSERS NUM_ACCEPTORS INITIAL_PROPOSALS\n\n\t" +
                "INITIAL_PROPOSALS is a comma-separated list of NUM_PROPOSERS strings.\n";
    }

    protected Proposer proposer(@NonNull Address address,
                                @NonNull Address[] acceptors,
                                int proposalNumber, int numProposers,
                                @NonNull String proposalValue) {
        return new Proposer(address, acceptors, proposalNumber, numProposers,
                proposalValue);

    }

    protected Acceptor acceptor(@NonNull Address address) {
        return new Acceptor(address);
    }

    @Override
    public SearchState getInitialState(String[] args) {
        int numProposers = Integer.parseInt(args[0]);
        int numAcceptors = Integer.parseInt(args[1]);
        String[] values = args[2].split(",");

        if (values.length != numProposers) {
            throw new IllegalArgumentException(
                    "Wrong number of proposal strings");
        }

        Map<Address, Node> nodes = new HashMap<>();
        Address[] proposers = new Address[numProposers];
        for (int i = 1; i <= numProposers; i++) {
            proposers[i - 1] = new LocalAddress("proposer" + i);
        }
        Address[] acceptors = new Address[numAcceptors];
        for (int i = 1; i <= numAcceptors; i++) {
            acceptors[i - 1] = new LocalAddress("acceptor" + i);
        }
        for (int i = 0; i < numProposers; i++) {
            Address a = proposers[i];
            nodes.put(a,
                    proposer(a, acceptors, i + 1, numProposers, values[i]));
        }
        for (Address a : acceptors) {
            nodes.put(a, acceptor(a));
        }
        StateGenerator g = StateGenerator.builder().serverSupplier(nodes::get)
                                         .clientSupplier(a -> null)
                                         .workloadSupplier(a -> null).build();
        SearchState s = new SearchState(g);
        for (Address a : proposers) {
            s.addServer(a);
        }
        for (Address a : acceptors) {
            s.addServer(a);
        }
        return s;
    }

    @Override
    public SearchSettings defaultSearchSettings() {
        SearchSettings settings = new SearchSettings();
        settings.addInvariant(StatePredicate.statePredicate("Integrity", s -> {
            Set<String> initialProposals = new HashSet<>();

            SearchState state = (SearchState) s;
            while (state.previous() != null) {
                state = state.previous();
            }

            for (Node n : state.nodes()) {
                if (!(n instanceof Proposer)) {
                    continue;
                }
                if (((Proposer) n).proposalValue != null) {
                    initialProposals.add(((Proposer) n).proposalValue);
                }
            }

            for (Node n : s.nodes()) {
                if (!(n instanceof Proposer)) {
                    continue;
                }
                if (((Proposer) n).decision != null) {
                    if (!initialProposals.contains(((Proposer) n).decision)) {
                        return false;
                    }
                }
            }

            return true;
        }));
        settings.addInvariant(StatePredicate.statePredicate("Agreement", s -> {
            String decided = null;
            for (Node n : s.servers()) {
                if (!(n instanceof Proposer)) {
                    continue;
                }
                Proposer p = (Proposer) n;
                if (p.decision != null && decided != null &&
                        !p.decision.equals(decided)) {
                    return false;
                }
                if (p.decision != null) {
                    decided = p.decision;
                }
            }
            return true;
        }));
        settings.addGoal(StatePredicate.statePredicate("Termination",
                s -> Streams.stream(s.nodes())
                            .filter(n -> n instanceof Proposer)
                            .allMatch(n -> ((Proposer) n).decision != null)));
        return settings;
    }
}

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
class Proposer extends Node {
    @VizIgnore final Address[] acceptors;
    @VizIgnore boolean hasProposed = false;
    @VizIgnore final int numProposers;

    String proposalValue;
    int proposalNumber;

    boolean prepareFinished = false;

    final Map<Address, PrepareAck> prepareAcks = new HashMap<>();
    final Set<Address> acceptAcks = new HashSet<>();

    String decision = null;

    Proposer(@NonNull Address address, @NonNull Address[] acceptors,
             int proposalNumber, int numProposers,
             @NonNull String proposalValue) {
        super(address);
        this.acceptors = acceptors;
        this.proposalValue = proposalValue;
        this.proposalNumber = proposalNumber;
        this.numProposers = numProposers;
    }

    @Override
    public void init() {
        set(new Propose(), 100);
    }

    void onPropose(Propose t) {
        if (hasProposed) {
            proposalNumber += numProposers;
        }
        hasProposed = true;
        prepareAcks.clear();
        acceptAcks.clear();
        prepareFinished = false;
        broadcast(new Prepare(proposalNumber), acceptors);
        set(t, 100);
    }

    void handlePrepareAck(PrepareAck m, Address sender) {
        if (m.proposalNumber != proposalNumber || prepareFinished) {
            return;
        }

        prepareAcks.put(sender, m);

        if (prepareAcks.size() * 2 > acceptors.length) {
            prepareFinished = true;
            var nonNullPAs = prepareAcks.values().stream()
                                        .filter(p -> p.accepted != null)
                                        .collect(Collectors.toList());
            if (!nonNullPAs.isEmpty()) {
                proposalValue = nonNullPAs.stream().reduce((p1, p2) ->
                                                  p1.accepted.getLeft() > p2.accepted.getLeft() ? p1 : p2)
                                          .get().accepted.getRight();
            }
            prepareAcks.clear();
            broadcast(new Accept(proposalNumber, proposalValue), acceptors);
        }
    }

    void handleAcceptAck(AcceptAck m, Address sender) {
        if (proposalNumber != m.proposalNumber) {
            return;
        }

        acceptAcks.add(sender);
        if (acceptAcks.size() * 2 > acceptors.length) {
            decision = proposalValue;
        }
    }
}

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
class Acceptor extends Node {
    Integer highestPrepared = null;
    Pair<Integer, String> highestAccepted = null;

    Acceptor(@NonNull Address address) {
        super(address);
    }

    @Override
    public void init() {
    }

    void handlePrepare(Prepare m, Address sender) {
        if (highestPrepared != null && highestPrepared >= m.proposalNumber) {
            return;
        }
        highestPrepared = m.proposalNumber;
        send(new PrepareAck(m.proposalNumber, highestAccepted), sender);
    }

    void handleAccept(Accept m, Address sender) {
        if (highestPrepared != null && highestPrepared > m.proposalNumber) {
            return;
        }
        send(new AcceptAck(m.proposalNumber), sender);
        if (highestAccepted == null ||
                highestAccepted.getLeft() < m.proposalNumber) {
            highestAccepted =
                    new ImmutablePair<>(m.proposalNumber, m.proposalValue);
        }
    }
}


@Data
class Prepare implements Message {
    final int proposalNumber;
}

@Data
class PrepareAck implements Message {
    final int proposalNumber;
    final Pair<Integer, String> accepted;
}

@Data
class Accept implements Message {
    final int proposalNumber;
    @NonNull final String proposalValue;
}

@Data
class AcceptAck implements Message {
    final int proposalNumber;
}

@Data
class Propose implements Timer {
    public String toString() {
        return "Propose";
    }
}
