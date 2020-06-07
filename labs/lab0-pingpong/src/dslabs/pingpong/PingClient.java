package dslabs.pingpong;

import com.google.common.base.Objects;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.pingpong.PingApplication.Ping;
import dslabs.pingpong.PingApplication.Pong;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dslabs.pingpong.PingTimer.RETRY_MILLIS;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PingClient extends Node implements Client {
    private final Address serverAddress;

    private Ping ping;
    private Pong pong;

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PingClient(Address address, Address serverAddress) {
        super(address);
        this.serverAddress = serverAddress;
    }

    @Override
    public synchronized void init() {
        // No initialization necessary
    }

    /* -------------------------------------------------------------------------
        Client Methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        if (!(command instanceof Ping)) {
            throw new IllegalArgumentException();
        }

        Ping p = (Ping) command;

        ping = p;
        pong = null;

        send(new PingRequest(p), serverAddress);
        set(new PingTimer(p), RETRY_MILLIS);
    }

    @Override
    public synchronized boolean hasResult() {
        return pong != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        while (pong == null) {
            wait();
        }

        return pong;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private synchronized void handlePongReply(PongReply m, Address sender) {
        if (Objects.equal(ping.value(), m.pong().value())) {
            pong = m.pong();
            notify();
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onPingTimer(PingTimer t) {
        if (Objects.equal(ping, t.ping()) && pong == null) {
            send(new PingRequest(ping), serverAddress);
            set(t, RETRY_MILLIS);
        }
    }
}
