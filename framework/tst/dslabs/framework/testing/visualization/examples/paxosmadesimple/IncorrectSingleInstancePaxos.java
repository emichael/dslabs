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

import dslabs.framework.Address;
import dslabs.framework.testing.junit.Lab;
import lombok.NonNull;

@Lab("PaxosMadeWrong")
public class IncorrectSingleInstancePaxos extends SingleInstancePaxos {
    @Override
    protected Proposer proposer(@NonNull Address address,
                                @NonNull Address[] acceptors,
                                int proposalNumber, int numProposers,
                                @NonNull String proposalValue) {
        return new BadProposer(address, acceptors, proposalNumber, numProposers,
                proposalValue);
    }
}

class BadProposer extends Proposer {

    BadProposer(@NonNull Address address, @NonNull Address[] acceptors,
                int proposalNumber, int numProposers,
                @NonNull String proposalValue) {
        super(address, acceptors, proposalNumber, numProposers, proposalValue);
    }

    @Override
    void handleAcceptAck(AcceptAck m, Address sender) {
        if (proposalNumber != m.proposalNumber) {
            return;
        }

        acceptAcks.add(sender);
        if (acceptAcks.size() * 2 >= acceptors.length - 1) {
            decision = proposalValue;
        }
    }
}
