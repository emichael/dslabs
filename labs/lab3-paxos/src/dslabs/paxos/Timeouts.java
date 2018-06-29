package dslabs.paxos;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class ClientTimeout implements Timeout {
    static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...
}

// Your code here...
