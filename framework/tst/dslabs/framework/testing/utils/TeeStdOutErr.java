/*
 * Copyright (c) 2023 Ellis Michael
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

package dslabs.framework.testing.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.apache.commons.io.output.TeeOutputStream;

public final class TeeStdOutErr {
  private static final PrintStream stdOut = System.out;
  private static final PrintStream stdErr = System.err;

  private static ByteArrayOutputStream stdOutTee = null;
  private static ByteArrayOutputStream stdErrTee = null;

  @Data
  public static final class TeeData {
    private final String stdOut;
    private final String stdErr;
  }

  public static synchronized void installTees() {
    assert stdOutTee == null && stdErrTee == null;
    stdOutTee = new ByteArrayOutputStream();
    stdErrTee = new ByteArrayOutputStream();
    System.setOut(new PrintStream(new TeeOutputStream(stdOut, stdOutTee)));
    System.setErr(new PrintStream(new TeeOutputStream(stdErr, stdErrTee)));
  }

  public static synchronized TeeData clearTees() {
    assert stdOutTee != null && stdErrTee != null;
    System.setOut(stdOut);
    System.setErr(stdErr);
    TeeData ret =
        new TeeData(
            stdOutTee.toString(StandardCharsets.UTF_8), stdErrTee.toString(StandardCharsets.UTF_8));
    stdOutTee = null;
    stdErrTee = null;
    return ret;
  }

  private TeeStdOutErr() {
    // Uninstantiable utility class
    throw new UnsupportedOperationException();
  }
}
