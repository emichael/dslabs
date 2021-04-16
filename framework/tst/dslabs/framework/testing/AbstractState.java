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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.framework.testing.utils.Cloning;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.java.Log;

/**
 * Base state representation that RunState and SearchState inherit from.
 */
@EqualsAndHashCode
@ToString(exclude = {"gen"})
@Log
public abstract class AbstractState implements Serializable {
    private final Map<Address, Node> servers;
    private final Map<Address, ClientWorker> clientWorkers;
    private final Map<Address, Node> clients;

    protected final transient StateGenerator gen;

    public abstract Iterable<MessageEnvelope> network();

    public abstract Iterable<TimerEnvelope> timers(Address address);

    protected abstract void setupNode(Address address);

    // TODO: rename this
    protected abstract void ensureNodeConfig(Address address);

    protected abstract void cleanupNode(Address address)
            throws InterruptedException;

    public AbstractState(Set<Address> servers, Set<Address> clientWorkers,
                         Set<Address> clients, StateGenerator stateGenerator) {
        // Check that no server or client has the same address
        Multiset<Address> addresses = HashMultiset.create();
        addresses.addAll(servers);
        addresses.addAll(clientWorkers);
        addresses.addAll(clients);

        for (Address a : addresses) {
            if (addresses.count(a) > 1) {
                throw new RuntimeException(
                        "Cannot have multiple nodes with same address");
            }
        }

        this.servers = stateGenerator.servers(servers);
        this.clientWorkers = stateGenerator.clientWorkers(clientWorkers);
        this.clients = stateGenerator.clients(clients);
        this.gen = stateGenerator;

        // Setup the nodes
        for (Address a : addresses()) {
            setupNode(a);
        }
    }

    protected AbstractState(AbstractState source, Address addressToClone) {
        servers = new HashMap<>(source.servers);
        clientWorkers = new HashMap<>(source.clientWorkers);
        clients = new HashMap<>(source.clients);
        gen = source.gen;

        if (addressToClone == null) {
            return;
        }

        if (servers.containsKey(addressToClone)) {
            servers.put(addressToClone,
                    Cloning.clone(servers.get(addressToClone)));
        } else if (clientWorkers.containsKey(addressToClone)) {
            clientWorkers.put(addressToClone,
                    Cloning.clone(clientWorkers.get(addressToClone)));
        } else if (clients.containsKey(addressToClone)) {
            clients.put(addressToClone,
                    Cloning.clone(clients.get(addressToClone)));
        } else {
            LOG.severe("Given address not found");
        }
    }

    public synchronized Iterable<Address> addresses() {
        return Iterables.concat(serverAddresses(), clientWorkerAddresses(),
                clientAddresses());
    }

    public synchronized Iterable<Node> servers() {
        return new LinkedList<>(servers.values());
    }

    public synchronized Iterable<Address> serverAddresses() {
        return new LinkedList<>(servers.keySet());
    }

    public synchronized Iterable<ClientWorker> clientWorkers() {
        return new LinkedList<>(clientWorkers.values());
    }

    public synchronized Iterable<Address> clientWorkerAddresses() {
        return new LinkedList<>(clientWorkers.keySet());
    }

    @SuppressWarnings("unchecked")
    protected synchronized <C extends Node & Client> Iterable<C> clients() {
        return clients.values().stream().map(c -> (C) c)::iterator;
    }

    public synchronized Iterable<Address> clientAddresses() {
        return new LinkedList<>(clients.keySet());
    }

    public synchronized Node server(Address address) {
        return servers.get(address);
    }

    public synchronized ClientWorker clientWorker(Address address) {
        return clientWorkers.get(address);
    }

    @SuppressWarnings("unchecked")
    protected synchronized <C extends Node & Client> C client(Address address) {
        return (C) clients.get(address);
    }

    public synchronized boolean clientWorkersDone() {
        return clientWorkers.values().stream().allMatch(ClientWorker::done);
    }

    public synchronized boolean resultsOk() {
        return clientWorkers.values().stream()
                            .allMatch(ClientWorker::resultsOk);
    }

    public synchronized Map<Address, List<Result>> results() {
        return clientWorkers.entrySet().stream().collect(
                Collectors.toMap(Entry::getKey, e -> e.getValue().results()));
    }

    public synchronized Iterable<Node> nodes() {
        return Iterables.concat(servers.values(), clients.values());
    }

    public synchronized int numNodes() {
        return servers.size() + clientWorkers.size() + clients.size();
    }

    public synchronized int numServers() {
        return servers.size();
    }

    public synchronized Node node(Address address) {
        if (servers.containsKey(address)) {
            return servers.get(address);
        }
        if (clientWorkers.containsKey(address)) {
            return clientWorkers.get(address);
        }
        return clients.get(address);
    }

    public synchronized boolean hasNode(Address address) {
        return servers.containsKey(address) ||
                clientWorkers.containsKey(address) ||
                clients.containsKey(address);
    }

    public synchronized void removeNode(Address address)
            throws InterruptedException {
        servers.remove(address);
        clientWorkers.remove(address);
        clients.remove(address);
        cleanupNode(address);
    }

    public synchronized void addServer(Address address) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return;
        }
        servers.put(address, gen.server(address));
        setupNode(address);
    }

    public synchronized void addClientWorker(Address address) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return;
        }
        clientWorkers.put(address, gen.clientWorker(address));
        setupNode(address);
    }

    public synchronized void addClientWorker(Address address,
                                             boolean recordCommandsAndResults) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return;
        }
        clientWorkers.put(address,
                gen.clientWorker(address, recordCommandsAndResults));
        setupNode(address);
    }

    public synchronized void addClientWorker(Address address,
                                             Workload workload) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return;
        }
        clientWorkers.put(address, gen.clientWorker(address, workload));
        setupNode(address);
    }

    public synchronized void addClientWorker(Address address, Workload workload,
                                             boolean recordCommandsAndResults) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return;
        }
        clientWorkers.put(address,
                gen.clientWorker(address, workload, recordCommandsAndResults));
        setupNode(address);
    }

    protected synchronized <C extends Node & Client> C addClient(
            Address address) {
        if (hasNode(address)) {
            LOG.severe("Re-adding an existing address to state");
            return null;
        }
        C client = gen.client(address);
        clients.put(address, client);
        setupNode(address);
        return client;
    }

    // TODO: maybe simplify some of these methods???
    public synchronized void addCommand(Command command) {
        for (Address clientAddress : clientWorkers.keySet()) {
            ensureNodeConfig(clientAddress);
            clientWorkers.get(clientAddress).addCommand(command);
        }
    }

    public synchronized void addCommand(String command) {
        for (Address clientAddress : clientWorkers.keySet()) {
            ensureNodeConfig(clientAddress);
            clientWorkers.get(clientAddress).addCommand(command);
        }
    }

    public synchronized void addCommand(Command command, Result result) {
        for (Address clientAddress : clientWorkers.keySet()) {
            ensureNodeConfig(clientAddress);
            clientWorkers.get(clientAddress).addCommand(command, result);
        }
    }

    public synchronized void addCommand(String command, String result) {
        for (Address clientAddress : clientWorkers.keySet()) {
            ensureNodeConfig(clientAddress);
            clientWorkers.get(clientAddress).addCommand(command, result);
        }
    }

    public synchronized void addCommand(Address clientAddress,
                                        Command command) {
        if (!clientWorkers.containsKey(clientAddress)) {
            return;
        }
        ensureNodeConfig(clientAddress);
        clientWorkers.get(clientAddress).addCommand(command);
    }

    public synchronized void addCommand(Address clientAddress, String command) {
        if (!clientWorkers.containsKey(clientAddress)) {
            return;
        }
        ensureNodeConfig(clientAddress);
        clientWorkers.get(clientAddress).addCommand(command);
    }

    public synchronized void addCommand(Address clientAddress, Command command,
                                        Result result) {
        if (!clientWorkers.containsKey(clientAddress)) {
            return;
        }
        ensureNodeConfig(clientAddress);
        clientWorkers.get(clientAddress).addCommand(command);
    }

    public synchronized void addCommand(Address clientAddress, String command,
                                        String result) {
        if (!clientWorkers.containsKey(clientAddress)) {
            return;
        }
        ensureNodeConfig(clientAddress);
        clientWorkers.get(clientAddress).addCommand(command, result);
    }
}
