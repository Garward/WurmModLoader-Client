#!/bin/bash
#
# WurmModLoader Client Launcher
#
# This script launches the Wurm Unlimited client with the modloader agent.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODLOADER_JAR="$SCRIPT_DIR/wurmmodloader-client-0.1.0.jar"

# Wurm installation paths
WURM_DIR="$HOME/.local/share/Steam/steamapps/common/Wurm Unlimited"
WURM_JRE="$WURM_DIR/runtime/jre1.8.0_172/bin/java"
CLIENT_DIR="$WURM_DIR/WurmLauncher"
CLIENT_JAR="$CLIENT_DIR/client.jar"
COMMON_JAR="$CLIENT_DIR/common.jar"
NATIVE_LIBS="$CLIENT_DIR/nativelibs"

# Validate paths
if [ ! -f "$MODLOADER_JAR" ]; then
    echo "Error: ModLoader JAR not found at: $MODLOADER_JAR"
    exit 1
fi

if [ ! -f "$WURM_JRE" ]; then
    echo "Error: Wurm JRE not found at: $WURM_JRE"
    echo "Make sure Wurm Unlimited is installed via Steam"
    exit 1
fi

if [ ! -f "$CLIENT_JAR" ]; then
    echo "Error: Client JAR not found at: $CLIENT_JAR"
    exit 1
fi

echo "=== WurmModLoader Client Launcher ==="
echo "ModLoader:  $MODLOADER_JAR"
echo "Client JAR: $CLIENT_JAR"
echo "Java:       $WURM_JRE"
echo ""

# Launch the client with the modloader as a Java agent
# Using Wurm's bundled JRE which includes JavaFX
"$WURM_JRE" -javaagent:"$MODLOADER_JAR" \
     -Djava.library.path="$NATIVE_LIBS" \
     -cp "$CLIENT_JAR:$COMMON_JAR" \
     com.wurmonline.client.launcherfx.WurmMain \
     "$@"
