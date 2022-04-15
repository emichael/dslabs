/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
 *                    Doug Woos (dwoos@cs.washington.edu)
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

package dslabs.framework.testing.visualization;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Streams;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.utils.GlobalSettings;
import dslabs.framework.testing.utils.Json;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Oddity client.
 *
 * @see Json
 */
public class VizClient {
    private static final boolean VERBOSE = false;

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 4343, DEFAULT_BROWSER_PORT = 3000;

    private final static String MSGTYPEFIELD = "msgtype";
    private final static String STATEIDFIELD = "state-id";
    private final static String MSGIDFIELD = "msg-id";
    private final static String TIMERIDFIELD = "timeout-id";

    private final static String MSGTYPEMSG = "msg";
    private final static String MSGTYPETIMER = "timeout";
    private final static String MSGTYPESTART = "start";
    private final static String MSGTYPEQUIT = "quit";
    private final static String MSGTYPEREGISTER = "register";

    private final String host;
    private final int port;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final SearchState state;
    private final StatePredicate invariant;
    private final String[] nodeNames;
    private final boolean trace;

    public VizClient(String host, int port, SearchState state,
                     StatePredicate invariant, String[] nodeNames,
                     boolean trace) {
        this.host = host;
        this.port = port;
        this.state = state;
        this.invariant = invariant;
        this.nodeNames = nodeNames;
        this.trace = trace;
    }

    public VizClient(SearchState state, StatePredicate invariant,
                     boolean trace) {
        this(DEFAULT_HOST, DEFAULT_PORT, state, invariant,
                Streams.stream(state.addresses()).map(Object::toString)
                       .toArray(String[]::new), trace);
    }

    public VizClient(SearchState state, boolean trace) {
        this(DEFAULT_HOST, DEFAULT_PORT, state, null,
                Streams.stream(state.addresses()).map(Object::toString)
                       .toArray(String[]::new), trace);
    }

    private void init() throws IOException {
        socket = new Socket(host, port);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    private void sendJson(String json) throws IOException {
        if (VERBOSE) {
            System.out.println(json);
        }

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        out.writeInt(jsonBytes.length);
        out.write(jsonBytes, 0, jsonBytes.length);
        out.flush();
    }

    private JsonNode readJson() throws IOException {
        int numBytes = in.readInt();
        byte[] bytes = new byte[numBytes];
        int numRead = 0;
        while (numRead < numBytes) {
            int newNumRead = in.read(bytes, numRead, numBytes - numRead);
            if (newNumRead == -1) {
                throw new IOException("Connection prematurely closed.");
            }
            numRead += newNumRead;
        }

        String json = new String(bytes, StandardCharsets.UTF_8);

        return Json.fromJson(json);
    }

    public void run(Boolean startOddity) throws IOException {
        if (trace) {
            System.out.println("Starting trace visualization client.");
        } else {
            System.out.println("Starting state visualization client.");
        }

        // Startup oddity from jar if necessary
        if (startOddity == null) {
            startOddity = GlobalSettings.startVizServer();
        }

        if (startOddity) {
            ProcessBuilder pb =
                    new ProcessBuilder("java", "-jar", "jars/oddity.jar");
            pb.redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT);
            final Process p = pb.start();

            // Kill oddity on exit
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));

            waitUntilPortListening(host, DEFAULT_BROWSER_PORT);

            // Startup the browser
            openURLInChrome("http://" + host + ":" + DEFAULT_BROWSER_PORT);
        }

        // Wait until we can communicate with server
        waitUntilPortListening(host, port);

        // Open socket
        init();

        // Render node names with surrounding quotation marks
        String[] wrappedNames = new String[nodeNames.length];
        for (int i = 0; i < nodeNames.length; i++) {
            wrappedNames[i] = String.format("\"%s\"", nodeNames[i]);
        }

        String registrationMessage;
        if (trace) {
            registrationMessage = String.format(
                    "{ \"%s\": \"%s\", \"names\": [ %s ], \"trace\": %s }",
                    MSGTYPEFIELD, MSGTYPEREGISTER,
                    String.join(",", wrappedNames),
                    Json.jsonTrace(state, invariant, "trace", 1));
        } else {
            registrationMessage =
                    String.format("{ \"%s\": \"%s\", \"names\": [ %s ] }",
                            MSGTYPEFIELD, MSGTYPEREGISTER,
                            String.join(",", wrappedNames));
        }
        sendJson(registrationMessage);
        JsonNode registrationResponse = readJson();
        if (!registrationResponse.get("ok").booleanValue()) {
            throw new RuntimeException("Registration failed");
        }
        System.out.println("Registered with viz.");

        // Make sure that the initial state has been serialized and assigned ids
        Json.toJson(state);

        while (!Thread.interrupted()) {
            JsonNode msg = readJson();
            String msgtype = msg.get(MSGTYPEFIELD).textValue();

            switch (msgtype) {
                case MSGTYPEMSG:
                    if (VERBOSE) {
                        System.out.println("Got message.");
                    }

                    String stateId = msg.get(STATEIDFIELD).textValue();
                    String msgId = msg.get(MSGIDFIELD).textValue();
                    SearchState s = Json.getState(stateId);
                    MessageEnvelope me = Json.getMessage(msgId);
                    if (s == null || me == null) {
                        throw new RuntimeException(
                                "Specified state or message doesn't exist");
                    }
                    SearchState result = s.stepMessage(me, null, true);
                    if (result == null) {
                        throw new RuntimeException("Couldn't deliver message");
                    }
                    sendJson(Json.toJson(result, invariant));
                    break;

                case MSGTYPETIMER:
                    if (VERBOSE) {
                        System.out.println("Got timer.");
                    }

                    stateId = msg.get(STATEIDFIELD).textValue();
                    String timerId = msg.get(TIMERIDFIELD).textValue();
                    s = Json.getState(stateId);
                    TimerEnvelope te = Json.getTimer(timerId);
                    if (te == null) {
                        throw new RuntimeException(
                                "Specified state or timer doesn't exist");
                    }
                    result = s.stepTimer(te, null, true);
                    if (result == null) {
                        throw new RuntimeException("Couldn't deliver timer");
                    }
                    sendJson(Json.toJson(result, invariant));
                    break;

                case MSGTYPESTART:
                    if (VERBOSE) {
                        System.out.println("Got start.");
                    }

                    sendJson(Json.toJson(state, invariant));
                    break;

                case MSGTYPEQUIT:
                    if (VERBOSE) {
                        System.out.println("Got shutdown command.");
                    }

                    Thread.currentThread().interrupt();
                    break;
            }
        }

        socket.close();
    }

    public void run() throws IOException {
        run(null);
    }

    private static void waitUntilPortListening(String host, int port)
            throws IOException {
        System.out.println(
                "Waiting for " + host + ":" + port + " to start listening.");

        while (!portIsListening(host, port)) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new IOException(
                        "Interrupted waiting for " + host + ":" + port +
                                " to open");
            }
        }
    }

    private static boolean portIsListening(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void openURLInChrome(String url) {
        System.out.println("Attempting to open " + url + " in Chrome.");

        String os = System.getProperty("os.name").toLowerCase();
        Runtime rt = Runtime.getRuntime();

        try {
            if (os.contains("win")) {
                rt.exec(new String[]{"start", "chrome", url});
            } else if (os.contains("mac")) {
                rt.exec(new String[]{"/usr/bin/open", "-a",
                        "/Applications/Google Chrome.app", url});
            } else {
                // Hopefully Linux...
                rt.exec(new String[]{"sh", "-c",
                        "google-chrome \"" + url + "\" || chromium-browser \"" +
                                url + "\""});
            }
        } catch (IOException ignored) {
            System.err.println("Couldn't open " + url);
        }
    }

    public static void main(String[] args) throws Exception {
        int labNum = Integer.parseInt(args[0]);
        String className = "dslabs.vizconfigs.Lab" + labNum + "VizConfig";
        VizConfig config =
                (VizConfig) Class.forName(className).getDeclaredConstructor()
                                 .newInstance();
        String[] vizArgs = Arrays.copyOfRange(args, 1, args.length);
        SearchState state = config.getInitialState(vizArgs);
        VizClient client = new VizClient(state, false);
        client.run();
    }
}
