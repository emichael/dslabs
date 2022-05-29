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

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.framework.Timer;
import dslabs.framework.VizIgnore;
import dslabs.framework.testing.utils.Cloning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import static org.apache.commons.lang3.math.NumberUtils.max;

@EqualsAndHashCode(of = {"client", "results"}, callSuper = false)
@ToString(of = {"client", "results"})
public final class ClientWorker extends Node {

    @Data
    private static class InterRequestTimer implements Timer {
    }

    // Defaults
    private static final boolean DEFAULT_RECORD_COMMANDS_AND_RESULTS = true;

    // Configuration
    private final Client client;
    @Getter @VizIgnore private final Workload workload;

    // Properties
    // TODO: move this to Workload
    @VizIgnore @Getter private final boolean recordCommandsAndResults;

    // Mutable state
    @VizIgnore private boolean initialized = false;
    @VizIgnore private boolean waitingOnResult = false;
    @VizIgnore private boolean waitingToSend = false;
    @VizIgnore private Command lastCommand = null;
    @VizIgnore private Result expectedResult = null;
    @VizIgnore private long lastSendTimeMillis;

    // Resulting state
    @Getter @VizIgnore private final List<Command> sentCommands =
            new ArrayList<>();
    @Getter private final List<Result> results = new ArrayList<>();
    @Getter @VizIgnore private boolean resultsOk = true;
    @Getter @VizIgnore private Pair<Result, Result> expectedAndReceived = null;
    @VizIgnore private long maxWaitTimeMillis = 0;


    public <C extends Node & Client> ClientWorker(@NonNull C client,
                                                  @NonNull Workload workload,
                                                  boolean recordCommandsAndResults) {
        super(client.address());
        this.client = client;
        this.recordCommandsAndResults = recordCommandsAndResults;

        // Clone operations on creation and reset it to completely avoid sharing
        this.workload = Cloning.clone(workload);
        this.workload.reset();
    }

    public <C extends Node & Client> ClientWorker(C client, Workload workload) {
        this(client, workload, DEFAULT_RECORD_COMMANDS_AND_RESULTS);
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

    public synchronized long maxWaitTimeMilis() {
        if (waitingOnResult) {
            return max(maxWaitTimeMillis,
                    System.currentTimeMillis() - lastSendTimeMillis);
        }
        return maxWaitTimeMillis;
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

                if (recordCommandsAndResults) {
                    sentCommands.add(lastCommand);
                    results.add(result);
                }

                maxWaitTimeMillis = max(maxWaitTimeMillis,
                        System.currentTimeMillis() - lastSendTimeMillis);

                if (workload.hasResults() &&
                        !Objects.equals(expectedResult, result)) {
                    resultsOk = false;
                    if (expectedAndReceived == null) {
                        expectedAndReceived =
                                new ImmutablePair<>(expectedResult, result);
                    }
                }

                waitingOnResult = false;
                lastCommand = null;
                expectedResult = null;
            }

            // Check if there's a next command to send
            if (waitingOnResult || waitingToSend || !workload.hasNext()) {
                break;
            }

            // If the workload is rate-limited, start the timer
            if (workload.isRateLimited()) {
                set(new InterRequestTimer(), workload.millisBetweenRequests());
                waitingToSend = true;
                break;
            }

            sendNextCommand();

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

    private void sendNextCommand() {
        if (workload.hasResults()) {
            Pair<Command, Result> commandAndResult =
                    workload.nextCommandAndResult(clientNode().address());
            lastCommand = commandAndResult.getLeft();
            expectedResult = commandAndResult.getRight();
            client.sendCommand(lastCommand);
        } else {
            lastCommand = workload.nextCommand(clientNode().address());
            client.sendCommand(lastCommand);
        }

        waitingToSend = false;
        waitingOnResult = true;
        lastSendTimeMillis = System.currentTimeMillis();
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
    public final synchronized void onTimer(Timer timer, Address destination) {
        if (timer instanceof InterRequestTimer) {
            sendNextCommand();
        } else {
            clientNode().onTimer(timer, destination);
        }
        sendNextCommandWhilePossible();
    }

    @Override
    public final void config(
            Consumer<Triple<Address, Address, Message>> messageAdder,
            Consumer<Triple<Address, Address[], Message>> batchMessageAdder,
            Consumer<Triple<Address, Timer, Pair<Integer, Integer>>> timerAdder,
            Consumer<Throwable> throwableCatcher, boolean logExceptions) {
        // TODO: make sure there's no overhead for having the config both places
        super.config(messageAdder, batchMessageAdder, timerAdder,
                throwableCatcher, logExceptions);
        clientNode().config(messageAdder, batchMessageAdder, timerAdder,
                throwableCatcher, logExceptions);
    }
}
