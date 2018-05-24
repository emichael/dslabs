package dslabs.clientserver;

import dslabs.framework.Timeout;
import lombok.Data;

@Data
final class ClientTimeout implements Timeout {
    private static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...

    @Override
    public int timeoutLengthMillis() {
        return CLIENT_RETRY_MILLIS;
    }
}
