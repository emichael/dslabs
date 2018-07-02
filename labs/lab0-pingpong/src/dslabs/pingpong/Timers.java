package dslabs.pingpong;

import dslabs.framework.Timer;
import dslabs.pingpong.PingApplication.Ping;
import lombok.Data;

@Data
final class PingTimer implements Timer {
    static final int RETRY_MILLIS = 10;
    private final Ping ping;
}
