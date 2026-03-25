#!/bin/bash

# FAPI 重连问题测试脚本
# 用法：
#   ./test_fapi_reconnect.sh server   # 启动服务端
#   ./test_fapi_reconnect.sh client   # 启动客户端
#   ./test_fapi_reconnect.sh build    # 编译项目

set -e

PROJECT_DIR="/Users/liuchangyong/Desktop/Freeverse"
cd "$PROJECT_DIR"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

function print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

function print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

function print_error() {
    echo -e "${RED}✗ $1${NC}"
}

function print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

function build_project() {
    print_header "Building FC-JDK Project"
    cd FC-JDK
    
    if mvn clean package -DskipTests; then
        print_success "Build completed successfully"
        print_info "JAR location: FC-JDK/target/FC-SDK.jar"
    else
        print_error "Build failed"
        exit 1
    fi
    
    cd ..
}

function start_server() {
    print_header "Starting FAPI Server"
    
    if [ ! -f "FC-JDK/target/FC-SDK.jar" ]; then
        print_error "FC-SDK.jar not found. Please build first:"
        echo "  ./test_fapi_reconnect.sh build"
        exit 1
    fi
    
    print_info "Server will start on port 9000 (default)"
    print_info "Press Ctrl+C to stop"
    echo ""
    
    java -cp FC-JDK/target/FC-SDK.jar fapi.StartFapiServer
}

function start_client() {
    print_header "Starting FAPI Client"
    
    if [ ! -f "FC-JDK/target/FC-SDK.jar" ]; then
        print_error "FC-SDK.jar not found. Please build first:"
        echo "  ./test_fapi_reconnect.sh build"
        exit 1
    fi
    
    print_info "Make sure the server is running first!"
    print_info "Default connection: 127.0.0.1:9000"
    echo ""
    
    java -cp FC-JDK/target/FC-SDK.jar fapi.StartFapiClient
}

function start_client_debug() {
    print_header "Starting FAPI Client (DEBUG mode)"
    
    if [ ! -f "FC-JDK/target/FC-SDK.jar" ]; then
        print_error "FC-SDK.jar not found. Please build first:"
        echo "  ./test_fapi_reconnect.sh build"
        exit 1
    fi
    
    print_info "Debug logging enabled for detailed output"
    echo ""
    
    java -Dorg.slf4j.simpleLogger.log.clients.ClientGroup=DEBUG \
         -Dorg.slf4j.simpleLogger.log.config.Settings=DEBUG \
         -Dorg.slf4j.simpleLogger.log.fapi.client.FapiClient=DEBUG \
         -cp FC-JDK/target/FC-SDK.jar fapi.StartFapiClient
}

function show_config() {
    print_header "FAPI Configuration"
    
    if [ -f "config/config.json" ]; then
        print_success "Configuration file found"
        
        # 提取 FAPI 相关配置
        echo ""
        echo "FAPI Provider:"
        cat config/config.json | grep -A 10 '"type": "FAPI"' || echo "No FAPI provider found"
        
        echo ""
        echo "FAPI Accounts:"
        cat config/config.json | grep -B 2 -A 5 'b4b621be5865ba21dc1731afd5df2f4aa47109f0e2dad3ca4fbf73298ff1aa20' || echo "No FAPI accounts found"
    else
        print_info "No configuration file yet (will be created on first run)"
    fi
}

function test_reconnect() {
    print_header "FAPI Reconnect Test"
    
    print_info "This test will:"
    echo "  1. Check if server is running"
    echo "  2. Start client (first connection)"
    echo "  3. Exit client"
    echo "  4. Start client again (reconnection test)"
    echo ""
    
    print_error "This is an interactive test. Please run manually:"
    echo ""
    echo "Terminal 1:"
    echo "  ./test_fapi_reconnect.sh server"
    echo ""
    echo "Terminal 2:"
    echo "  ./test_fapi_reconnect.sh client"
    echo "  # Test APIs, then exit"
    echo "  ./test_fapi_reconnect.sh client"
    echo "  # Should reconnect successfully!"
    echo ""
}

function show_help() {
    cat << EOF
${BLUE}FAPI Reconnect Test Script${NC}

${GREEN}Usage:${NC}
  ./test_fapi_reconnect.sh <command>

${GREEN}Commands:${NC}
  build          Compile the FC-JDK project
  server         Start FAPI Server
  client         Start FAPI Client
  client-debug   Start FAPI Client with debug logging
  config         Show current FAPI configuration
  test           Show reconnect test instructions
  help           Show this help message

${GREEN}Quick Start:${NC}
  1. Build the project:
     ${YELLOW}./test_fapi_reconnect.sh build${NC}
  
  2. Start server (Terminal 1):
     ${YELLOW}./test_fapi_reconnect.sh server${NC}
  
  3. Start client (Terminal 2):
     ${YELLOW}./test_fapi_reconnect.sh client${NC}
  
  4. Test APIs, then exit client
  
  5. Start client again (should reconnect):
     ${YELLOW}./test_fapi_reconnect.sh client${NC}

${GREEN}Expected Behavior After Fix:${NC}
  ✓ First connection: Full discovery (HELLO + PING)
  ✓ Second connection: Full discovery with auto-config update
  ✓ Third connection: Fast path (PING only)
  ✓ Detailed logs showing connection process
  ✓ Clear error messages if any issues occur

${GREEN}Troubleshooting:${NC}
  - If client fails to connect, check server is running
  - For detailed logs, use: ./test_fapi_reconnect.sh client-debug
  - View config: ./test_fapi_reconnect.sh config
  - Check ports match (default 9000)

${GREEN}Files:${NC}
  - Configuration: config/config.json
  - Server logs: logs/
  - Documentation: FAPI_RECONNECT_FIX.md
                  FAPI_RECONNECT_SUMMARY.md

EOF
}

# Main script
case "${1:-help}" in
    build)
        build_project
        ;;
    server)
        start_server
        ;;
    client)
        start_client
        ;;
    client-debug)
        start_client_debug
        ;;
    config)
        show_config
        ;;
    test)
        test_reconnect
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac




