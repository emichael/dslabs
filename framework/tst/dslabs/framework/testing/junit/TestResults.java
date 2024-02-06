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

package dslabs.framework.testing.junit;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Jacksonized
@JsonIgnoreProperties
class TestResults implements Serializable {
  @Value
  @Builder
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Jacksonized
  static class TestResult implements Serializable {
    String labName;
    Integer part;
    Integer testNumber;
    String testDescription;
    String testMethodName;
    Integer pointsAvailable;
    Integer pointsEarned;
    @Singular ImmutableList<String> testCategories;

    // TODO: use different System.out/System.err print streams in the
    //  framework itself and log those here (to differentiate user/framework
    //  logging).

    // TODO: somehow timestamp the saved stdout/stderr logs so that we can retain the interleaving
    //  between the streams

    String stdOutLog;
    Boolean stdOutTruncated;
    String stdErrLog;
    Boolean stdErrTruncated;

    Instant startTime;
    Instant endTime;
  }

  @Singular ImmutableList<TestResult> results;

  Instant startTime;
  Instant endTime;

  private static void configureObjectMapper(ObjectMapper mapper) {
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new JavaTimeModule());
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
  }

  public void writeJsonToFile(String fileName) throws IOException {
    JsonMapper mapper = new JsonMapper();
    configureObjectMapper(mapper);
    mapper.writeValue(new File(fileName), this);
  }
}
