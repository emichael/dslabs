package dslabs.primarybackup;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class PingCheckTimeout implements Timeout {
    static final int PING_CHECK_MILLIS = 100;
}

@Data
final class PingTimeout implements Timeout {
    static final int PING_MILLIS = 25;
}

@Data
final class ClientTimeout implements Timeout {
    static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...
}

// Your code here...
