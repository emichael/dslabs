class View:
    def __init__(self, viewnum=0, primary=None, backup=None):
        self.viewnum = viewnum
        self.primary = primary
        self.backup = backup

    def __repr__(self):
        return f"View(viewnum={self.viewnum}, primary={self.primary}, backup={self.backup})"


class ViewServer:
    def __init__(self):
        self.current_view = View()
        self.pending_view = None
        self.ping_status = {}  # Tracks servers' ping statuses
        self.primary_acknowledged = False

    def ping(self, server, viewnum):
        self.ping_status[server] = True

        # Handle acknowledgment of the current view
        if server == self.current_view.primary and viewnum == self.current_view.viewnum:
            self.primary_acknowledged = True

        # If the current view is not acknowledged, do not update views
        if not self.primary_acknowledged:
            return self.current_view

        # Handle view updates
        if server not in (self.current_view.primary, self.current_view.backup):
            self.update_view(server)

        return self.current_view

    def update_view(self, new_server):
        # Promote backup to primary if primary failed
        if not self.ping_status.get(self.current_view.primary):
            self.pending_view = View(
                self.current_view.viewnum + 1,
                self.current_view.backup,
                new_server if self.current_view.backup else None,
            )
        # Assign new backup if needed
        elif not self.ping_status.get(self.current_view.backup):
            self.pending_view = View(
                self.current_view.viewnum + 1,
                self.current_view.primary,
                new_server,
            )

        # Apply pending view if conditions are met
        if self.pending_view:
            self.current_view = self.pending_view
            self.pending_view = None
            self.primary_acknowledged = False

    def get_view(self):
        return self.current_view

    def handle_ping_check_timer(self):
        # Mark servers that did not ping as dead
        self.ping_status = {server: False for server in self.ping_status}

    def process_ping_check(self):
        for server in list(self.ping_status):
            if not self.ping_status[server]:
                del self.ping_status[server]


# Example usage
if __name__ == "__main__":
    vs = ViewServer()

    print(vs.ping("S1", 0))  # First server becomes primary
    print(vs.ping("S2", 0))  # Second server becomes backup
    vs.handle_ping_check_timer()  # Simulate a timer check
    print(vs.get_view())
