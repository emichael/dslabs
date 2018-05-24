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

package dslabs.framework.testing.search;

import dslabs.framework.Address;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimeoutEnvelope;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Transition {
    private final MessageEnvelope message;
    private final TimeoutEnvelope timeout;

    Transition(MessageEnvelope messageEnvelope) {
        this.message = messageEnvelope;
        this.timeout = null;
    }

    Transition(TimeoutEnvelope timeout) {
        this.message = null;
        this.timeout = timeout;
    }

    public boolean isMessage() {
        return message != null;
    }

    public boolean isTimeout() {
        return timeout != null;
    }

    public Address locationRootAddress() {
        if (isMessage()) {
            return message.to().rootAddress();
        } else {
            return timeout.to().rootAddress();
        }
    }

    @Override
    public String toString() {
        if (isMessage()) {
            return message.toString();
        } else {
            return timeout.toString();
        }
    }
}
