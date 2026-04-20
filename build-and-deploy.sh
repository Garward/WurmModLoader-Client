#!/usr/bin/env bash
#
# WurmModLoader Client Build and Deploy Script
# Complete automation: build + deploy in one command
#
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd "$PROJECT_DIR"

echo -e "${CYAN}======================================================================${NC}"
echo -e "${CYAN}🤖 WurmModLoader Client: Full Build & Deploy Automation${NC}"
echo -e "${CYAN}======================================================================${NC}"
echo ""

# Step 1: Build
echo -e "${BLUE}[1/2]${NC} ${YELLOW}Building project...${NC}"
echo ""

if ./build.sh; then
    echo ""
    echo -e "${GREEN}✅ Build completed successfully!${NC}"
else
    echo ""
    echo -e "${RED}❌ Build failed! Deployment aborted.${NC}"
    exit 1
fi

echo ""
echo -e "${CYAN}======================================================================${NC}"
echo ""

# Step 2: Deploy
echo -e "${BLUE}[2/2]${NC} ${YELLOW}Deploying to client...${NC}"
echo ""

if ./deploy.sh; then
    echo ""
    echo -e "${CYAN}======================================================================${NC}"
    echo -e "${GREEN}🎉 Full Automation Complete!${NC}"
    echo -e "${CYAN}======================================================================${NC}"
    echo ""
    echo -e "${GREEN}✓ Built successfully${NC}"
    echo -e "${GREEN}✓ Deployed to client${NC}"
    echo ""
    echo -e "${YELLOW}Your client patcher is ready to test!${NC}"
    echo ""
    exit 0
else
    echo ""
    echo -e "${RED}❌ Deployment failed!${NC}"
    exit 1
fi
