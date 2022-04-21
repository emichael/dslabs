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
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;


/**
 * <p>Represents either a timer to fire or a message to deliver.
 *
 * @see dslabs.framework.testing.search.SearchState
 * @see dslabs.framework.testing.runner.RunState
 */
@Getter
@EqualsAndHashCode
public class Event implements Serializable {
    // invariant: exactly one of the {message, timer} fields is null
    private final MessageEnvelope message;
    private final TimerEnvelope timer;

    public Event(MessageEnvelope messageEnvelope) {
        this.message = messageEnvelope;
        this.timer = null;
    }

    public Event(TimerEnvelope timer) {
        this.message = null;
        this.timer = timer;
    }

    public boolean isMessage() {
        return message != null;
    }

    public boolean isTimer() {
        return timer != null;
    }

    public Address locationRootAddress() {
        if (isMessage()) {
            return message.to().rootAddress();
        } else {
            return timer.to().rootAddress();
        }
    }

    @Override
    public String toString() {
        if (isMessage()) {
            return message.toString();
        } else {
            return timer.toString();
        }
    }
}
