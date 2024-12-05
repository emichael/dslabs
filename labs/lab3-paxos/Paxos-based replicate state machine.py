import threading
import time
from collections import defaultdict

class PaxosServer:
    def __init__(self, name, peers):
        self.name = name
        self.peers = peers
        self.ballot = 0
        self.accepted = {}  # {slot: (ballot, value)}
        self.decided = {}  # {slot: value}
        self.proposals = {}  # {slot: value}
        self.promises = defaultdict(list)  # {slot: [(sender, ballot, value)]}
        self.lock = threading.Lock()
        self.is_leader = False
        self.leader = None
        self.heartbeat_timer = 3
        self.last_heartbeat = time.time()

    def propose(self, slot, value):
        with self.lock:
            if slot in self.decided:
                print(f"{self.name}: Slot {slot} already decided.")
                return

            self.ballot += 1
            self.proposals[slot] = value
            print(f"{self.name}: Proposing {value} in slot {slot} with ballot {self.ballot}")
            self.broadcast("prepare", slot, self.ballot, value)

    def handle_prepare(self, sender, slot, ballot, value):
        with self.lock:
            if ballot >= self.ballot:
                self.ballot = ballot
                if slot not in self.accepted:
                    self.accepted[slot] = (ballot, value)
                print(f"{self.name}: Promising ballot {ballot} for slot {slot}")
                self.reply(sender, "promise", slot, ballot, self.accepted[slot][1])

    def handle_promise(self, sender, slot, ballot, value):
        with self.lock:
            self.promises[slot].append((sender, ballot, value))
            if len(self.promises[slot]) > len(self.peers) // 2:
                max_ballot = max(self.promises[slot], key=lambda x: x[1])[1]
                chosen_value = max(self.promises[slot], key=lambda x: x[1])[2]
                print(f"{self.name}: Consensus reached for slot {slot}: {chosen_value}")
                self.decide(slot, chosen_value)

    def decide(self, slot, value):
        with self.lock:
            self.decided[slot] = value
            print(f"{self.name}: Decided slot {slot} -> {value}")
            self.broadcast("decide", slot, self.ballot, value)

    def handle_decide(self, sender, slot, ballot, value):
        with self.lock:
            self.decided[slot] = value
            print(f"{self.name}: Learned decision for slot {slot} -> {value}")

    def broadcast(self, msg_type, slot, ballot, value):
        for peer in self.peers:
            peer.receive_message(self.name, msg_type, slot, ballot, value)

    def reply(self, recipient, msg_type, slot, ballot, value):
        recipient.receive_message(self.name, msg_type, slot, ballot, value)

    def receive_message(self, sender, msg_type, slot, ballot, value):
        if msg_type == "prepare":
            self.handle_prepare(sender, slot, ballot, value)
        elif msg_type == "promise":
            self.handle_promise(sender, slot, ballot, value)
        elif msg_type == "decide":
            self.handle_decide(sender, slot, ballot, value)

    def send_heartbeat(self):
        while True:
            if self.is_leader:
                print(f"{self.name}: Sending heartbeat.")
                for peer in self.peers:
                    peer.receive_heartbeat(self.name)
            time.sleep(self.heartbeat_timer)

    def receive_heartbeat(self, sender):
        with self.lock:
            if self.leader != sender:
                print(f"{self.name}: New leader detected: {sender}")
                self.leader = sender
            self.last_heartbeat = time.time()

    def monitor_heartbeat(self):
        while True:
            with self.lock:
                if self.is_leader:
                    continue
                if time.time() - self.last_heartbeat > self.heartbeat_timer * 2:
                    print(f"{self.name}: Leader timeout detected, starting election.")
                    self.start_election()
            time.sleep(self.heartbeat_timer)

    def start_election(self):
        with self.lock:
            self.ballot += 1
            print(f"{self.name}: Starting election with ballot {self.ballot}")
            self.is_leader = True
            self.leader = self.name
            self.broadcast("prepare", -1, self.ballot, None)

# Initialize servers
s1 = PaxosServer("S1", [])
s2 = PaxosServer("S2", [])
s3 = PaxosServer("S3", [])

# Link servers
s1.peers = [s2, s3]
s2.peers = [s1, s3]
s3.peers = [s1, s2]

# Start threads
for server in [s1, s2, s3]:
    threading.Thread(target=server.send_heartbeat, daemon=True).start()
    threading.Thread(target=server.monitor_heartbeat, daemon=True).start()

# Simulate Paxos proposal
time.sleep(2)  # Allow heartbeat initialization
threading.Thread(target=lambda: s1.propose(1, "X")).start()
threading.Thread(target=lambda: s2.propose(2, "Y")).start()
