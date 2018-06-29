package dslabs.pingpong;

import dslabs.framework.Timeout;
import dslabs.pingpong.PingApplication.Ping;
import lombok.Data;

@Data
final class PingTimeout implements Timeout {
    static final int RETRY_MILLIS = 10;
    private final Ping ping;
}
