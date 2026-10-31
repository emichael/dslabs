package dslabs.paxos;

public enum PaxosLogSlotStatus {
  /** No command is known by the server for this slot. */
  EMPTY,
  /** A command has been tentatively accepted by this server. */
  ACCEPTED,
  /** The server knows a command to be permanently chosen for this slot. */
  CHOSEN,
  /** The command in this slot has been garbage-collected at the server. */
  CLEARED
}
