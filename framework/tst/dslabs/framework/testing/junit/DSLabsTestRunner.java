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

import dslabs.framework.testing.utils.GlobalSettings;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class DSLabsTestRunner extends BlockJUnit4ClassRunner {
    private static final boolean TIMEOUTS_ENABLED =
            GlobalSettings.timeoutsEnabled();

    public DSLabsTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
        /*
         * Sort the tests here to make sure they are sorted both when running
         * from the command line and also in IntelliJ without having to mark
         * every class as sorted on method name. This is a hack, to be sure.
         * Methods within a test class are sorted multiple times. It works,
         * though.
         */
        sort(new TestOrder());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Statement withPotentialTimeout(FrameworkMethod method,
                                             Object test, Statement next) {
        if (TIMEOUTS_ENABLED) {
            return super.withPotentialTimeout(method, test, next);
        } else {
            return next;
        }
    }
}
