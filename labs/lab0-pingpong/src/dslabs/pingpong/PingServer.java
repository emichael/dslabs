package dslabs.pingpong;

import dslabs.framework.Address;
import dslabs.framework.Node;
import dslabs.pingpong.PingApplication.Pong;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PingServer extends Node {
    private final PingApplication app = new PingApplication();

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PingServer(Address address) {
        super(address);
    }

    @Override
    public void init() {
        // No initialization necessary
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handlePingRequest(PingRequest m, Address sender) {
        Pong p = app.execute(m.ping());
        send(new PongReply(p), sender);
    }
}
