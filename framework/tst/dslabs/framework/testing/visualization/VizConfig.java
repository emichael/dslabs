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

package dslabs.framework.testing.visualization;

import dslabs.framework.Address;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * In order to support starting the visualizer from an initial state from the
 * command-line, each lab needs a way to convert a list of argument into a
 * concrete state.
 *
 * Sub-classes only need override {@link VizConfig#getInitialState(String[])}.
 * The other methods are provided for convenience. However, the instructions in
 * the READMEs describing how to start the visualizer specify the default
 * behavior as taking the arguments: "numServers numClients WORKLOAD_STRING".
 * And deviation from that convention should be clearly documented in that lab's
 * README.
 *
 * Sub-classes should be labeled with @Lab and should be in a package beginning
 * with "dslabs".
 */
public abstract class VizConfig {
    public SearchState getInitialState(String[] args) {
        int numServers = Integer.parseInt(args[0]);
        int numClients = Integer.parseInt(args[1]);
        // The input is in one of the following formats:
        //
        // <# servers> <# clients> <uniform workload>
        //
        // <# servers> <# clients> <client 1 workload> ... <client n workload>
        if (args.length != 3 && args.length != 2 + numClients) {
            throw new IllegalArgumentException(
                    "Please provide either a single workload for all " +
                            "clients or a separate workload for each client.");
        }
        List<List<String>> commands = new LinkedList<>();
        if (args.length == 3) {
            // Same workload for all clients
            List<String> singleWorkload = commands(args[2]);
            for (int i = 0; i < numClients; i++) {
                commands.add(singleWorkload);
            }
        } else {
            // Specific workload for each client
            for (int i = 0; i < numClients; i++) {
                commands.add(commands(args[2 + i]));
            }
        }
        return getInitialState(numServers, numClients, commands);
    }

    /**
     * Should always be called after {@link #getInitialState(String[])}.
     */
    public SearchSettings defaultSearchSettings() {
        return new SearchSettings();
    }

    /**
     * Printed if there is an error parsing args, after "Usage: ".
     */
    public String argumentHelpString() {
        return null;
    }

    protected SearchState getInitialState(int numServers, int numClients,
                                          List<List<String>> commands) {
        List<Address> servers = new LinkedList<>();
        if (numServers == 1) {
            servers.add(new LocalAddress("server"));
        } else {
            for (int i = 1; i <= numServers; i++) {
                servers.add(new LocalAddress("server" + i));
            }
        }

        List<Address> clients = new LinkedList<>();
        if (numClients == 1) {
            clients.add(new LocalAddress("client"));
        } else {
            for (int i = 1; i <= numClients; i++) {
                clients.add(new LocalAddress("client" + i));
            }
        }

        SearchState searchState =
                new SearchState(stateGenerator(servers, clients, commands));

        for (Address server : servers) {
            searchState.addServer(server);
        }
        for (Address client : clients) {
            searchState.addClientWorker(client);
        }

        return searchState;
    }

    protected StateGenerator stateGenerator(List<Address> servers,
                                            List<Address> clients,
                                            List<List<String>> commands) {
        return stateGenerator(commands);
    }

    protected StateGenerator stateGenerator(List<List<String>> commands) {
        return stateGenerator(Collections.emptyList(), Collections.emptyList(),
                commands);
    }

    protected static List<String> commands(String commands) {
        return Arrays.asList(commands.split(","));
    }
}
