package dslabs.primarybackup;

import dslabs.framework.Address;
import java.io.Serializable;
import lombok.Data;

@Data
class View implements Serializable {
    private final int viewNum;
    private final Address primary, backup;
}
