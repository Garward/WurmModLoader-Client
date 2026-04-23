#!/usr/bin/env bash
#
# WurmModLoader Client Smart Deploy Script
# Deploys client patcher to Wurm client directory
# Only copies files that have changed
#
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# === CONFIG ===
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CLIENT_DIR="${WURM_CLIENT_DIR:-$HOME/.local/share/Steam/steamapps/common/Wurm Unlimited/WurmLauncher}"
DEPLOY_DIR="$CLIENT_DIR"
TEMP_EXTRACT="/tmp/wurmmodloader-client-deploy-$$"

# Find latest distribution ZIP (auto-detect version)
DIST_ZIP=$(ls -t "$PROJECT_DIR"/build/distributions/WurmModloader-Client-*.zip 2>/dev/null | head -1)

# Track changes
COPIED_FILES=0
SKIPPED_FILES=0
ERRORS=0

echo -e "${BLUE}======================================================================${NC}"
echo -e "${BLUE}🚀 WurmModLoader Client Smart Deploy${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo ""

# === CHECKS ===
if [ -z "$DIST_ZIP" ] || [ ! -f "$DIST_ZIP" ]; then
    echo -e "${RED}❌ Distribution ZIP not found!${NC}"
    echo -e "${YELLOW}Expected location:${NC} $PROJECT_DIR/build/distributions/"
    echo -e "${YELLOW}Run build first:${NC} ./build.sh"
    exit 1
fi

if [ ! -d "$CLIENT_DIR" ]; then
    echo -e "${RED}❌ Client directory not found!${NC}"
    echo -e "${YELLOW}Expected:${NC} $CLIENT_DIR"
    echo -e "${YELLOW}Is Wurm Unlimited installed via Steam?${NC}"
    exit 1
fi

echo -e "${CYAN}📦 Distribution:${NC} $(basename "$DIST_ZIP")"
echo -e "${CYAN}🎯 Deploy Dir:${NC} $DEPLOY_DIR"
echo ""

# === EXTRACT DISTRIBUTION ===
echo -e "${YELLOW}📂 Extracting distribution to temp...${NC}"
mkdir -p "$TEMP_EXTRACT"
unzip -q "$DIST_ZIP" -d "$TEMP_EXTRACT"
echo -e "${GREEN}✓${NC} Extracted to $TEMP_EXTRACT"
echo ""

# === FUNCTION: Copy if changed ===
copy_if_changed() {
    local src="$1"
    local dest="$2"
    local desc="$3"

    if [ ! -f "$src" ]; then
        echo -e "  ${RED}✗${NC} ${desc} - source not found"
        ERRORS=$((ERRORS + 1))
        return 1
    fi

    # Create destination directory if needed
    mkdir -p "$(dirname "$dest")"

    # Check if file exists and is identical
    if [ -f "$dest" ]; then
        if cmp -s "$src" "$dest"; then
            echo -e "  ${BLUE}⊙${NC} ${desc} - unchanged"
            SKIPPED_FILES=$((SKIPPED_FILES + 1))
            return 0
        fi
    fi

    # Copy the file
    if cp "$src" "$dest"; then
        echo -e "  ${GREEN}✓${NC} ${desc}"
        COPIED_FILES=$((COPIED_FILES + 1))
        return 0
    else
        echo -e "  ${RED}✗${NC} ${desc} - copy failed"
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# === CREATE DEPLOY DIRECTORY ===
mkdir -p "$DEPLOY_DIR"

# === DEPLOY UBER-JAR ===
echo -e "${BLUE}======================================================================${NC}"
echo -e "${YELLOW}📚 Deploying Client Patcher${NC}"
echo -e "${BLUE}======================================================================${NC}"

# Copy the uber-JAR
for jar in "$TEMP_EXTRACT"/*.jar; do
    if [ -f "$jar" ]; then
        basename_jar=$(basename "$jar")
        copy_if_changed "$jar" "$DEPLOY_DIR/$basename_jar" "Patcher: $basename_jar"
    fi
done

# Purge stale modloader jars (old versions) so the agent/bootstrap always picks
# up the latest. Newest deployed jar wins.
LATEST_MODLOADER=$(ls -t "$DEPLOY_DIR"/wurmmodloader-client-*.jar 2>/dev/null | head -1)
for stale in "$DEPLOY_DIR"/wurmmodloader-client-*.jar; do
    if [ "$stale" != "$LATEST_MODLOADER" ]; then
        rm -f "$stale" && echo -e "  ${YELLOW}⌫${NC} Removed stale: $(basename "$stale")"
    fi
done

echo ""

# === PATCH client.jar ON DISK ===
# Bakes widenings + wurmModLoaderBootstrap into client.jar so Steam launches
# (which don't go through LaunchConfig.ini VMParams the same way) still load
# the modloader. Idempotent: restores from .backup first, then re-patches.
echo -e "${BLUE}======================================================================${NC}"
echo -e "${YELLOW}🔨 Patching client.jar on disk${NC}"
echo -e "${BLUE}======================================================================${NC}"

WURM_JRE="$CLIENT_DIR/../runtime/jre1.8.0_172/bin/java"
CLIENT_JAR="$CLIENT_DIR/client.jar"
CLIENT_BACKUP="$CLIENT_DIR/client.jar.backup"

if [ ! -x "$WURM_JRE" ]; then
    echo -e "${YELLOW}⚠️  Wurm JRE not found at $WURM_JRE; falling back to system java${NC}"
    WURM_JRE="java"
fi

if [ -f "$CLIENT_BACKUP" ]; then
    echo -e "  ${BLUE}↻${NC} Restoring client.jar from backup before re-patch..."
    cp "$CLIENT_BACKUP" "$CLIENT_JAR"
fi

(cd "$CLIENT_DIR" && "$WURM_JRE" -jar "$LATEST_MODLOADER") >/dev/null 2>&1 && \
    echo -e "  ${GREEN}✓${NC} client.jar patched ($(basename "$LATEST_MODLOADER"))" || \
    { echo -e "  ${RED}✗${NC} client.jar patch failed — run manually: cd \"$CLIENT_DIR\" && \"$WURM_JRE\" -jar \"$LATEST_MODLOADER\""; ERRORS=$((ERRORS + 1)); }

echo ""

# === DEPLOY LAUNCHER SCRIPTS ===
echo -e "${BLUE}======================================================================${NC}"
echo -e "${YELLOW}🚀 Deploying Launcher Scripts${NC}"
echo -e "${BLUE}======================================================================${NC}"

# Copy launcher scripts directly to WurmLauncher/
if [ -d "$TEMP_EXTRACT/scripts" ]; then
    for script in "$TEMP_EXTRACT/scripts"/*; do
        if [ -f "$script" ]; then
            basename_script=$(basename "$script")
            dest="$DEPLOY_DIR/$basename_script"
            copy_if_changed "$script" "$dest" "Script: $basename_script"
            # Make scripts executable
            chmod +x "$dest" 2>/dev/null || true
        fi
    done
fi

echo ""

# === DEPLOY DOCUMENTATION ===
echo -e "${BLUE}======================================================================${NC}"
echo -e "${YELLOW}📄 Deploying Documentation${NC}"
echo -e "${BLUE}======================================================================${NC}"

# Copy documentation files
for doc in "$TEMP_EXTRACT"/*.md; do
    if [ -f "$doc" ]; then
        basename_doc=$(basename "$doc")
        copy_if_changed "$doc" "$DEPLOY_DIR/$basename_doc" "Docs: $basename_doc"
    fi
done

echo ""

# === CLEANUP ===
echo -e "${YELLOW}🧹 Cleaning up...${NC}"
rm -rf "$TEMP_EXTRACT"
echo -e "${GREEN}✓${NC} Temp files removed"
echo ""

# === SUMMARY ===
echo -e "${BLUE}======================================================================${NC}"
echo -e "${GREEN}📊 Deployment Summary${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo -e "${GREEN}✓ Copied:${NC}    $COPIED_FILES files"
echo -e "${BLUE}⊙ Unchanged:${NC} $SKIPPED_FILES files"
if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}✗ Errors:${NC}    $ERRORS"
    echo ""
    echo -e "${RED}⚠️  Deployment completed with errors${NC}"
    exit 1
else
    echo ""
    echo -e "${GREEN}✅ Deployment Complete!${NC}"
    echo ""
    echo -e "${CYAN}Installation Location:${NC}"
    echo -e "  $DEPLOY_DIR/"
    echo ""
    echo -e "${CYAN}Next steps:${NC}"
    echo -e "  1. Launch client with:"
    echo -e "     ${BLUE}cd $DEPLOY_DIR && ./launch-client.sh${NC}"
    echo -e "  2. Or directly:"
    echo -e "     ${BLUE}$DEPLOY_DIR/launch-client.sh${NC}"
    echo -e "  3. Watch for patcher output in client console"
    echo ""
    exit 0
fi
