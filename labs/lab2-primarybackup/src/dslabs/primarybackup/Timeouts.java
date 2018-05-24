package dslabs.primarybackup;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class PingCheckTimeout implements Timeout {
    public static final int PING_CHECK_INTERVAL_MILLIS = 100;

    @Override
    public int timeoutLengthMillis() {
        return PING_CHECK_INTERVAL_MILLIS;
    }
}

@Data
final class PingTimeout implements Timeout {
    public static final int PING_INTERVAL_MILLIS = 25;

    @Override
    public int timeoutLengthMillis() {
        return PING_INTERVAL_MILLIS;
    }
}

@Data
final class ClientTimeout implements Timeout {
    private static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...

    @Override
    public int timeoutLengthMillis() {
        return CLIENT_RETRY_MILLIS;
    }
}

// Your code here...
