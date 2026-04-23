#!/usr/bin/env bash
#
# WurmModLoader Client Patcher
#
# Bakes the modloader bootstrap + GUI access widenings into your Wurm
# Unlimited client.jar on disk. After patching, launching the game through
# Steam (or WurmLauncher directly) will load mods automatically — no
# -javaagent flag, no custom launch script required.
#
# Idempotent: if a client.jar.backup already exists, it is restored before
# re-patching, so you can run this safely after every modloader update.
#
# To restore vanilla:  mv client.jar.backup client.jar
#
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Locate WurmLauncher directory. If this script lives in WurmLauncher/, use
# that; otherwise autodetect the default Steam install.
if [ -f "$SCRIPT_DIR/client.jar" ]; then
    CLIENT_DIR="$SCRIPT_DIR"
else
    CLIENT_DIR="$HOME/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher"
fi

CLIENT_JAR="$CLIENT_DIR/client.jar"
CLIENT_BACKUP="$CLIENT_DIR/client.jar.backup"

echo -e "${CYAN}======================================================================${NC}"
echo -e "${CYAN}🔨 WurmModLoader Client Patcher${NC}"
echo -e "${CYAN}======================================================================${NC}"
echo ""

if [ ! -f "$CLIENT_JAR" ]; then
    echo -e "${RED}✗ client.jar not found at: $CLIENT_JAR${NC}"
    echo -e "${YELLOW}  Place this script alongside client.jar, or install Wurm via Steam.${NC}"
    exit 1
fi

# Pick the newest modloader jar next to client.jar.
MODLOADER_JAR=$(ls -t "$CLIENT_DIR"/wurmmodloader-client-*.jar 2>/dev/null | head -1 || true)
if [ -z "$MODLOADER_JAR" ]; then
    echo -e "${RED}✗ No wurmmodloader-client-*.jar found in $CLIENT_DIR${NC}"
    echo -e "${YELLOW}  Copy the modloader JAR there first, then re-run this script.${NC}"
    exit 1
fi

# Prefer Wurm's bundled JRE (Java 8) — the standalone patcher uses
# Java-8-era Javassist calls.
WURM_JRE="$CLIENT_DIR/../runtime/jre1.8.0_172/bin/java"
if [ ! -x "$WURM_JRE" ]; then
    echo -e "${YELLOW}⚠ Wurm JRE not found at $WURM_JRE — falling back to system java${NC}"
    WURM_JRE="java"
fi

echo -e "${CYAN}Client dir:${NC} $CLIENT_DIR"
echo -e "${CYAN}Modloader: ${NC} $(basename "$MODLOADER_JAR")"
echo -e "${CYAN}Java:      ${NC} $WURM_JRE"
echo ""

# If already patched, restore from backup first so re-patching is idempotent.
if [ -f "$CLIENT_BACKUP" ]; then
    echo -e "${BLUE}↻${NC} Existing backup detected — restoring vanilla client.jar before re-patch..."
    cp "$CLIENT_BACKUP" "$CLIENT_JAR"
fi

echo -e "${YELLOW}🔨 Patching...${NC}"
echo ""

if (cd "$CLIENT_DIR" && "$WURM_JRE" -jar "$MODLOADER_JAR"); then
    echo ""
    echo -e "${GREEN}======================================================================${NC}"
    echo -e "${GREEN}✅ client.jar patched successfully${NC}"
    echo -e "${GREEN}======================================================================${NC}"
    echo -e "  Backup: ${CYAN}$CLIENT_BACKUP${NC}"
    echo -e "  You can now launch the game normally (Steam, WurmLauncher, etc.)."
    echo -e "  To restore vanilla: ${BLUE}mv \"$CLIENT_BACKUP\" \"$CLIENT_JAR\"${NC}"
    echo ""
else
    echo ""
    echo -e "${RED}✗ Patch failed. Check the output above.${NC}"
    exit 1
fi
