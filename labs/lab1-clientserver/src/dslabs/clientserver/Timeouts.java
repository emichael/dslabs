package dslabs.clientserver;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class ClientTimeout implements Timeout {
    static final int RETRY_MILLIS = 100;

    // Your code here...
}
