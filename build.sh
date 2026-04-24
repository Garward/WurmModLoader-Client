#!/usr/bin/env bash
#
# WurmModLoader Client Build Script
# Runs clean build and creates distribution
#
set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo -e "${BLUE}======================================================================${NC}"
echo -e "${BLUE}🛠️  WurmModLoader Client Build Script${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo ""

cd "$PROJECT_DIR"

# Show current status
echo -e "${YELLOW}📂 Project Directory:${NC} $PROJECT_DIR"
echo -e "${YELLOW}🔧 Gradle Version:${NC}"
./gradlew --version | grep "Gradle\|JVM" | head -2
echo ""

# Run build
echo -e "${BLUE}======================================================================${NC}"
echo -e "${GREEN}🏗️  Running: ./gradlew clean build dist${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo ""

START_TIME=$(date +%s)

if ./gradlew clean build dist; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    echo ""
    echo -e "${BLUE}======================================================================${NC}"
    echo -e "${GREEN}✅ Build Successful!${NC}"
    echo -e "${BLUE}======================================================================${NC}"
    echo -e "${GREEN}⏱️  Build Time: ${DURATION}s${NC}"
    echo ""

    # Show what was built
    echo -e "${YELLOW}📦 Distribution:${NC}"
    ls -lh build/distributions/*.zip 2>/dev/null || echo "  No distribution ZIP found"
    echo ""

    echo -e "${YELLOW}📚 Uber-JAR:${NC}"
    ls -lh wurmmodloader-client-patcher/build/libs/wurmmodloader-client-*.jar 2>/dev/null | grep -v sources | grep -v javadoc
    echo ""

    echo -e "${YELLOW}📄 Distribution Contents:${NC}"
    unzip -l build/distributions/WurmModloader-Client-*.zip 2>/dev/null | grep -E "(jar|sh|bat|md)" | tail -n +4 | head -n -2
    echo ""

    # Sync client SDK jars into sibling CommunityMods/libs/ (if the repo exists).
    COMMUNITY_LIBS="$PROJECT_DIR/../WurmModLoader-CommunityMods/libs"
    if [ -d "$COMMUNITY_LIBS" ]; then
        echo -e "${YELLOW}🔗 Syncing client SDK jars → $COMMUNITY_LIBS${NC}"
        rm -f "$COMMUNITY_LIBS"/wurmmodloader-client-{api,core,legacy}-*.jar
        for m in api core legacy; do
            for f in "$PROJECT_DIR"/wurmmodloader-client-"$m"/build/libs/wurmmodloader-client-"$m"-*.jar; do
                case "$f" in *-sources.jar|*-javadoc.jar) continue ;; esac
                [ -f "$f" ] && cp "$f" "$COMMUNITY_LIBS/" && echo "   + $(basename "$f")"
            done
        done
        echo ""
    fi

    echo -e "${GREEN}Ready to deploy! Run:${NC} ${BLUE}./deploy.sh${NC}"
    echo ""

    exit 0
else
    echo ""
    echo -e "${RED}======================================================================${NC}"
    echo -e "${RED}❌ Build Failed!${NC}"
    echo -e "${RED}======================================================================${NC}"
    echo -e "${RED}Check the error output above for details.${NC}"
    echo ""
    exit 1
fi
