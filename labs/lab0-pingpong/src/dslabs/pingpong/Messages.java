package dslabs.pingpong;

import dslabs.framework.Message;
import dslabs.pingpong.PingApplication.Ping;
import dslabs.pingpong.PingApplication.Pong;
import lombok.Data;

@Data
class PingRequest implements Message {
    private final Ping ping;
}

@Data
class PongReply implements Message {
    private final Pong pong;
}
