package dslabs.pingpong;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class PingTimeout implements Timeout {
    private static final int PING_TIMEOUT_MILLIS = 10;

    @Override
    public int timeoutLengthMillis() {
        return PING_TIMEOUT_MILLIS;
    }
}
