#!/bin/bash
#
# WurmModLoader Client Installer
#
# This script installs the WurmModLoader for Wurm Unlimited client.
# It patches client.jar to automatically load mods when the game starts.
#

set -e

echo "======================================================================="
echo "🛠️  WurmModLoader Client Installer"
echo "======================================================================="
echo ""

# Detect Steam installation
STEAM_DIR="$HOME/.local/share/Steam/steamapps/common/Wurm Unlimited"
WURM_LAUNCHER="$STEAM_DIR/WurmLauncher"
CLIENT_JAR="$WURM_LAUNCHER/client.jar"

if [ ! -f "$CLIENT_JAR" ]; then
    echo "❌ Error: Could not find Wurm Unlimited installation"
    echo "   Expected: $CLIENT_JAR"
    echo ""
    echo "   Please make sure Wurm Unlimited is installed via Steam."
    exit 1
fi

echo "✓ Found Wurm Unlimited installation"
echo "  Location: $WURM_LAUNCHER"
echo ""

# Check if already patched
if [ -f "$WURM_LAUNCHER/client.jar.backup" ]; then
    echo "⚠️  Client appears to be already patched (backup exists)"
    echo ""
    read -p "Re-patch anyway? This will restore from backup first. (y/N) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Restoring from backup..."
        cp "$WURM_LAUNCHER/client.jar.backup" "$CLIENT_JAR"
        rm "$WURM_LAUNCHER/client.jar.backup"
    else
        echo "Installation cancelled."
        exit 0
    fi
fi

# Build the patcher if needed
echo "🔧 Building WurmModLoader..."
./gradlew clean build dist -q
echo "✓ Build complete"
echo ""

# Extract and deploy
echo "📦 Deploying to Wurm Unlimited..."
DIST_ZIP=$(ls -t build/distributions/WurmModloader-Client-*.zip | head -1)
TEMP_DIR=$(mktemp -d)

unzip -q "$DIST_ZIP" -d "$TEMP_DIR"

# Copy modloader JAR
cp "$TEMP_DIR"/wurmmodloader-client-*.jar "$WURM_LAUNCHER/"
MODLOADER_JAR=$(ls -t "$WURM_LAUNCHER"/wurmmodloader-client-*.jar | head -1)
echo "✓ Copied modloader: $(basename $MODLOADER_JAR)"

# Clean up temp
rm -rf "$TEMP_DIR"

# Run the patcher
echo ""
echo "🔨 Patching client.jar..."
echo ""
java -jar "$MODLOADER_JAR"

echo ""
echo "======================================================================="
echo "✅ Installation Complete!"
echo "======================================================================="
echo ""
echo "The Wurm Unlimited client is now patched to load mods automatically."
echo ""
echo "📝 Next Steps:"
echo "  1. Place mod JARs in: $WURM_LAUNCHER/mods/"
echo "  2. Launch Wurm Unlimited through Steam normally"
echo "  3. Mods will load automatically at startup"
echo ""
echo "📚 Files installed:"
echo "  • $(basename $MODLOADER_JAR)"
echo "  • client.jar (patched)"
echo "  • client.jar.backup (original)"
echo ""
echo "🔧 To uninstall:"
echo "  cd \"$WURM_LAUNCHER\""
echo "  mv client.jar.backup client.jar"
echo "  rm wurmmodloader-client-*.jar"
echo ""
