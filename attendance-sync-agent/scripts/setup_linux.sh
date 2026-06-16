#!/bin/bash

# Determine execution path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
INSTALL_DIR="$( cd "$DIR/.." && pwd )"

echo "Setting up background SyncAgent autostart on Linux..."
echo "Install Directory: $INSTALL_DIR"
echo ""

# Setup user systemd directory
USER_SYSTEMD_DIR="$HOME/.config/systemd/user"
mkdir -p "$USER_SYSTEMD_DIR"

# Template substitution
sed "s|{INSTALL_DIR}|$INSTALL_DIR|g" "$DIR/attendance-sync-agent.service" > "$USER_SYSTEMD_DIR/attendance-sync-agent.service"

echo "Loading systemd user configurations..."
systemctl --user daemon-reload
systemctl --user enable attendance-sync-agent.service
systemctl --user start attendance-sync-agent.service

if [ $? -eq 0 ]; then
    echo "[SUCCESS] Linux autostart configured. SyncAgent is running headlessly as a systemd user daemon."
    echo "To check service status, run: systemctl --user status attendance-sync-agent.service"
    echo "To view agent logs, run: journalctl --user -u attendance-sync-agent.service -f"
else
    echo "[ERROR] Failed to start systemd user service."
fi
