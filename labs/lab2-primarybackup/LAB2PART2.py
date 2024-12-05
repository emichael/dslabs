class PBServer:
    def __init__(self, view_server, server_id):
        self.view_server = view_server  # Reference to the ViewServer
        self.server_id = server_id
        self.current_view = None
        self.app_state = {}  # Key/Value store
        self.last_applied_seq = 0  # For ensuring correct order of operations
        self.is_primary = False
        self.is_backup = False

    def ping(self):
        # Periodically ping the ViewServer to fetch the latest view
        view = self.view_server.ping(self.server_id, self.current_view.viewnum if self.current_view else 0)
        self.update_role(view)

    def update_role(self, view):
        self.current_view = view
        self.is_primary = (self.current_view.primary == self.server_id)
        self.is_backup = (self.current_view.backup == self.server_id)

    def handle_client_request(self, op, key, value=None):
        if not self.is_primary:
            return {"error": "Not the primary server"}

        # Process operation and sync with backup
        if op == "GET":
            return {"value": self.app_state.get(key, None)}
        elif op == "PUT":
            self.app_state[key] = value
            self.last_applied_seq += 1
            self.sync_with_backup(op, key, value)
            return {"success": True}

    def sync_with_backup(self, op, key, value):
        if self.current_view.backup:
            # Forward the operation to the backup
            self.forward_to_backup(op, key, value)

    def forward_to_backup(self, op, key, value):
        # Simulated message to the backup server
        backup_server = self.view_server.get_server(self.current_view.backup)
        if backup_server:
            backup_server.apply_operation(op, key, value)

    def apply_operation(self, op, key, value):
        if self.is_backup:
            if op == "PUT":
                self.app_state[key] = value

    def initialize_backup(self):
        if self.is_primary and self.current_view.backup:
            backup_server = self.view_server.get_server(self.current_view.backup)
            if backup_server:
                # Send the full state to the backup
                backup_server.receive_full_state(self.app_state)

    def receive_full_state(self, state):
        if self.is_backup:
            self.app_state = state


class PBClient:
    def __init__(self, view_server):
        self.view_server = view_server
        self.current_view = None

    def get_view(self):
        if not self.current_view:
            self.current_view = self.view_server.get_view()
        return self.current_view

    def request(self, op, key, value=None):
        primary = self.get_view().primary
        if not primary:
            return {"error": "No primary server available"}

        # Send request to the primary server
        primary_server = self.view_server.get_server(primary)
        if primary_server:
            response = primary_server.handle_client_request(op, key, value)
            if "error" in response:
                # Fetch new view if there's an error
                self.current_view = self.view_server.get_view()
            return response
        return {"error": "Primary server unreachable"}
