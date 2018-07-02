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

package dslabs.framework;

/**
 * <p>Clients are a special case of {@link Node}. Client nodes will have
 * handlers for {@link Message}s and {@link Timer}s like all {@link Node}s but
 * also provide an interface for interactively sending {@link Command}s and
 * receiving {@link Result}s in a system.
 *
 * <p>Clients in this framework are "closed-loop" clients. That is, they will
 * only have one outstanding command at any given time. You are free to assume
 * this throughout the labs; the test code always waits for the previous command
 * to return a result before sending the next.
 *
 * <p><b>IMPORTANT:</b> Client interface methods must be properly {@code
 * synchronized} with {@link Message} handlers and {@link Timer} handlers, since
 * the event handlers are invoked concurrently with the code using the client.
 * The easiest way to do this is to add the {@code synchronized} modifier to all
 * of the aforementioned methods. Furthermore, {@link Client#hasResult()} should
 * return immediately, while {@link Client#getResult()} should block until the
 * client has received a result for the latest command it sent.
 */
public interface Client {

    /**
     * Send a {@link Command} to the system with the given operation. Should
     * send the {@link Command} and return immediately without blocking,
     * sleeping, or starting other threads.
     *
     * @param command
     *         the {@link Application} command to send
     */
    void sendCommand(Command command);

    /**
     * Whether or not a {@link Result} was received for the previously sent
     * {@link Command}. Should return immediately without blocking, sleeping, or
     * starting other threads.
     *
     * @return whether the {@link Result} has been received
     */
    boolean hasResult();

    /**
     * <p>Returns the value from the {@link Result} for the previously sent
     * {@link Command}. Should block until there is such a {@link Result} or the
     * waiting thread is interrupted; this of course means that this method
     * should relinquish all locks/monitors it holds preventing messages from
     * being received while it is waiting. If the calling thread is interrupted
     * while waiting for a {@link Result}, an {@link InterruptedException}
     * should be thrown. Successive calls to this method (and {@link
     * Client#hasResult()}) that are not interrupted and are not interleaved
     * with calls to {@link Client#sendCommand(Command)} should continue to
     * return the same value.
     *
     * @return the value corresponding to the previously sent {@link Command}
     *
     * @throws InterruptedException
     *         when the calling thread is interrupted while blocking
     */
    Result getResult() throws InterruptedException;
}
