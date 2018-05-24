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

package dslabs.framework.testing.junit;

public class InvariantViolationError extends AssertionError {
    /**
     * Constructs an InvariantViolationError with no detail message.
     */
    public InvariantViolationError() {

    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified object, which is converted to a string as defined in
     * section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     * <p> If the specified object is an instance of {@code Throwable}, it
     * becomes the <i>cause</i> of the newly constructed assertion error.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     * @see Throwable#getCause()
     */
    public InvariantViolationError(Object detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>boolean</code>, which is converted to a string
     * as defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(boolean detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>char</code>, which is converted to a string as
     * defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(char detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>int</code>, which is converted to a string as
     * defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(int detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>long</code>, which is converted to a string as
     * defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(long detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>float</code>, which is converted to a string as
     * defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(float detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs an InvariantViolationError with its detail message derived
     * from the specified <code>double</code>, which is converted to a string as
     * defined in section 15.18.1.1 of <cite>The Java&trade; Language
     * Specification</cite>.
     *
     * @param detailMessage
     *         value to be used in constructing detail message
     */
    public InvariantViolationError(double detailMessage) {
        super(detailMessage);
    }

    /**
     * Constructs a new {@code InvariantViolationError} with the specified
     * detail message and cause.
     *
     * <p>Note that the detail message associated with {@code cause} is
     * <i>not</i> automatically incorporated in this error's detail message.
     *
     * @param message
     *         the detail message, may be {@code null}
     * @param cause
     *         the cause, may be {@code null}
     * @since 1.7
     */
    public InvariantViolationError(String message, Throwable cause) {
        super(message, cause);
    }
}
