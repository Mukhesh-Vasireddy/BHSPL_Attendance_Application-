#!/bin/bash

# Determine execution path
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
INSTALL_DIR="$( cd "$DIR/.." && pwd )"

echo "Setting up background SyncAgent autostart on macOS..."
echo "Install Directory: $INSTALL_DIR"
echo ""

# Copy plist and replace placeholders
LAUNCH_AGENTS_DIR="$HOME/Library/LaunchAgents"
mkdir -p "$LAUNCH_AGENTS_DIR"

PLIST_FILE="$LAUNCH_AGENTS_DIR/com.bhspl.syncagent.plist"

# Template substitution
sed "s|{INSTALL_DIR}|$INSTALL_DIR|g" "$DIR/com.bhspl.syncagent.plist" > "$PLIST_FILE"

# Adjust permissions for macOS security rules
chmod 644 "$PLIST_FILE"

echo "Loading macOS launchd agent..."
# Unload previous to prevent duplication warnings
launchctl unload "$PLIST_FILE" 2>/dev/null
launchctl load -w "$PLIST_FILE"

if [ $? -eq 0 ]; then
    echo "[SUCCESS] macOS autostart configured. SyncAgent is running headlessly as a launchd agent."
    echo "To monitor stdout logs, run: tail -f $INSTALL_DIR/target/sync_agent_stdout.log"
    echo "To monitor stderr logs, run: tail -f $INSTALL_DIR/target/sync_agent_stderr.log"
else
    echo "[ERROR] Failed to load macOS launchd agent."
fi
