package dslabs.paxos;

public enum PaxosLogSlotStatus {
    EMPTY,    // no command is known by the server for this slot
    ACCEPTED, // a command has been tentatively accepted by this server
    CHOSEN,   // the server knows a command to be permanently chosen for this slot
    CLEARED   // the command in this slot has been garbage-collected at the server
}
