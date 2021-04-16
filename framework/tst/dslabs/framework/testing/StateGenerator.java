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
import dslabs.framework.Client;
import dslabs.framework.Node;
import dslabs.framework.testing.utils.SerializableFunction;
import dslabs.framework.testing.utils.SerializableSupplier;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;

@Builder
public class StateGenerator implements Serializable {
    @NonNull private final SerializableFunction<Address, Node> serverSupplier;
    @NonNull private final SerializableFunction<Address, Client> clientSupplier;
    @NonNull private final SerializableFunction<Address, Workload>
            workloadSupplier;

    public Node server(Address address) {
        return serverSupplier.apply(address);
    }

    public Map<Address, Node> servers(Set<Address> addresses) {
        return addresses.stream().collect(
                Collectors.toMap(Function.identity(), this::server));
    }

    @SuppressWarnings("unchecked")
    public <C extends Node & Client> C client(Address address) {
        return (C) clientSupplier.apply(address);
    }

    public Map<Address, Node> clients(Set<Address> addresses) {
        return addresses.stream().collect(
                Collectors.toMap(Function.identity(), this::client));
    }

    @SuppressWarnings("unchecked")
    public <C extends Node & Client> ClientWorker clientWorker(
            Address address) {
        return new ClientWorker((C) clientSupplier.apply(address),
                workloadSupplier.apply(address));
    }

    @SuppressWarnings("unchecked")
    public <C extends Node & Client> ClientWorker clientWorker(Address address,
                                                               boolean recordCommandsAndResults) {
        return new ClientWorker((C) clientSupplier.apply(address),
                workloadSupplier.apply(address), recordCommandsAndResults);
    }

    public ClientWorker clientWorker(Address address, Workload workload) {
        return new ClientWorker((Node & Client) clientSupplier.apply(address),
                workload);
    }

    public ClientWorker clientWorker(Address address, Workload workload,
                                     boolean recordCommandsAndResults) {
        return new ClientWorker((Node & Client) clientSupplier.apply(address),
                workload, recordCommandsAndResults);
    }

    public Map<Address, ClientWorker> clientWorkers(Set<Address> addresses) {
        return addresses.stream().collect(Collectors.toMap(Function.identity(),
                a -> new ClientWorker((Node & Client) clientSupplier.apply(a),
                        workloadSupplier.apply(a))));
    }

    public Map<Address, ClientWorker> clientWorkers(Set<Address> addresses,
                                                    boolean recordCommandsAndResults) {
        return addresses.stream().collect(Collectors.toMap(Function.identity(),
                a -> new ClientWorker((Node & Client) clientSupplier.apply(a),
                        workloadSupplier.apply(a), recordCommandsAndResults)));
    }

    public Map<Address, ClientWorker> clientWorkers(Set<Address> addresses,
                                                    Workload workload) {
        return addresses.stream().collect(Collectors.toMap(Function.identity(),
                a -> new ClientWorker((Node & Client) clientSupplier.apply(a),
                        workload)));
    }

    public Map<Address, ClientWorker> clientWorkers(Set<Address> addresses,
                                                    Workload workload,
                                                    boolean recordCommandsAndResults) {
        return addresses.stream().collect(Collectors.toMap(Function.identity(),
                a -> new ClientWorker((Node & Client) clientSupplier.apply(a),
                        workload, recordCommandsAndResults)));
    }

    public static class StateGeneratorBuilder {
        public StateGeneratorBuilder serverSupplier(
                SerializableFunction<Address, Node> serverSupplier) {
            this.serverSupplier = serverSupplier;
            return this;
        }

        public StateGeneratorBuilder serverSupplier(
                SerializableSupplier<Node> serverSupplier) {
            this.serverSupplier = __ -> serverSupplier.get();
            return this;
        }

        public <C extends Node & Client> StateGeneratorBuilder clientSupplier(
                SerializableFunction<Address, C> clientSupplier) {
            this.clientSupplier = clientSupplier::apply;
            return this;
        }

        public <C extends Node & Client> StateGeneratorBuilder clientSupplier(
                SerializableSupplier<C> clientSupplier) {
            this.clientSupplier = __ -> clientSupplier.get();
            return this;
        }

        public StateGeneratorBuilder workloadSupplier(
                SerializableFunction<Address, Workload> workloadSupplier) {
            this.workloadSupplier = workloadSupplier;
            return this;
        }

        public StateGeneratorBuilder workloadSupplier(Workload workload) {
            this.workloadSupplier = __ -> workload;
            return this;
        }
    }
}
