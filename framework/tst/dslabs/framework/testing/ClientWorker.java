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

import com.fasterxml.jackson.annotation.JsonIgnore;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.framework.Timeout;
import dslabs.framework.testing.utils.Cloning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

@EqualsAndHashCode(of = {"client", "results"}, callSuper = false)
@ToString(of = {"client", "results"})
public final class ClientWorker extends Node {
    private static final boolean DEFAULT_RECORD_RESULTS = true,
            DEFAULT_RECORD_FINISH_TIMES = false;

    private final Client client;
    @JsonIgnore private final Workload workload;

    // Properties
    @JsonIgnore @Getter private final boolean recordResults;
    @JsonIgnore @Getter private final boolean recordFinishTimes;

    // Mutable state
    @JsonIgnore private boolean initialized = false;
    @JsonIgnore private boolean waitingOnResult = false;
    @JsonIgnore private Result expectedResult = null;

    // Resulting state
    @Getter private final List<Result> results = new ArrayList<>();
    @Getter @JsonIgnore private final List<Long> finishTimes =
            new ArrayList<>();
    @Getter @JsonIgnore private boolean resultsOk = true;
    @Getter @JsonIgnore private Pair<Result, Result> expectedAndReceived = null;

    // TODO: log start time

    public <C extends Node & Client> ClientWorker(@NonNull C client,
                                                  @NonNull Workload workload,
                                                  boolean recordResults,
                                                  boolean recordFinishTimes) {
        super(client.address());
        this.client = client;
        this.recordResults = recordResults;
        this.recordFinishTimes = recordFinishTimes;

        // Clone operations on creation and reset it to completely avoid sharing
        this.workload = Cloning.clone(workload);
        this.workload.reset();
    }

    public <C extends Node & Client> ClientWorker(C client, Workload workload) {
        this(client, workload, DEFAULT_RECORD_RESULTS,
                DEFAULT_RECORD_FINISH_TIMES);
    }

    public synchronized void addCommand(Command command) {
        workload.add(command);
        sendNextCommandWhilePossible();
    }

    public synchronized void addCommand(String command) {
        workload.add(command);
        sendNextCommandWhilePossible();
    }

    public synchronized void addCommand(Command command, Result result) {
        workload.add(command, result);
        sendNextCommandWhilePossible();
    }

    public synchronized void addCommand(String command, String result) {
        workload.add(command, result);
        sendNextCommandWhilePossible();
    }

    private void sendNextCommandWhilePossible() {
        // If we haven't been initialized, don't send next command
        if (!initialized) {
            return;
        }

        while (true) {
            // If we're waiting on a result and there is one, add it
            if (waitingOnResult && client.hasResult()) {
                Result result;
                try {
                    result = client.getResult();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (recordResults) {
                    results.add(result);
                }

                if (recordFinishTimes) {
                    finishTimes.add(System.currentTimeMillis());
                }

                if (workload.hasResults() &&
                        !Objects.equals(expectedResult, result)) {
                    resultsOk = false;
                    if (expectedAndReceived == null) {
                        expectedAndReceived =
                                new ImmutablePair<>(expectedResult, result);
                    }
                }

                waitingOnResult = false;
                expectedResult = null;
            }

            // If we can send a command, send it
            if (!waitingOnResult && workload.hasNext()) {
                if (workload.hasResults()) {
                    Pair<Command, Result> commandAndResult =
                            workload.nextCommandAndResult(
                                    clientNode().address());
                    expectedResult = commandAndResult.getRight();
                    client.sendCommand(commandAndResult.getLeft());
                } else {
                    client.sendCommand(
                            workload.nextCommand(clientNode().address()));
                }
                waitingOnResult = true;
            } else {
                break;
            }

            // Make sure we haven't been interrupted
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        // If we're done with the operations now, notify all waiting threads
        if (done()) {
            notifyAll();
        }
    }

    public synchronized boolean done() {
        return !waitingOnResult && !workload.hasNext();
    }

    public synchronized void waitUntilDone() throws InterruptedException {
        while (!done()) {
            wait();
        }
    }

    public synchronized void waitUntilDone(long timeoutMillis)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();

        while (!done()) {
            long timeLeft =
                    timeoutMillis - (System.currentTimeMillis() - startTime);
            if (timeLeft > 0) {
                wait(timeLeft);
            } else {
                return;
            }
        }
    }

    private Node clientNode() {
        return (Node) client;
    }

    @Override
    public final synchronized void init() {
        initialized = true;
        clientNode().init();
        sendNextCommandWhilePossible();
    }

    @Override
    public final synchronized void handleMessage(Message message,
                                                 Address sender,
                                                 Address destination) {
        clientNode().handleMessage(message, sender, destination);
        sendNextCommandWhilePossible();
    }

    @Override
    public final synchronized void onTimeout(Timeout timeout,
                                             Address destination) {
        clientNode().onTimeout(timeout, destination);
        sendNextCommandWhilePossible();
    }

    @Override
    public final void config(
            Consumer<Triple<Address, Address, Message>> messageAdder,
            Consumer<Triple<Address, Address[], Message>> batchMessageAdder,
            Consumer<Pair<Address, Timeout>> timeoutAdder) {
        clientNode().config(messageAdder, batchMessageAdder, timeoutAdder);
    }
}
