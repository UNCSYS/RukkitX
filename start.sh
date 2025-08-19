#!/bin/bash

# kill_java.sh - 自动查找并杀死Java进程的脚本

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# 获取Java进程PID列表
pids=$(ps | grep java | grep -v grep | awk '{print $1}')

if [ -z "$pids" ]; then
    echo -e "${RED}没有找到运行的Java进程${NC}"
    java -Dfile.encoding=UTF-8 -Djava.library.path=. -cp Rukkit-0.9.4-dev.jar:libs/* cn.rukkit.RukkitLauncher
    exit 0
fi

echo -e "\n找到以下Java进程PID: $pids"

# 确认操作
read -p "确定要杀死这些Java进程吗? (y/n) " -n 1 -r
echo    # 换行
if [[ $REPLY =~ ^[Yy]$ ]]; then
    # 杀死进程
    for pid in $pids; do
        echo -e "${RED}正在杀死进程 $pid...${NC}"
        kill -9 $pid
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}成功杀死进程 $pid${NC}"
        else
            echo -e "${RED}杀死进程 $pid 失败${NC}"
        fi
    done
else
    echo -e "${GREEN}操作已取消${NC}"
fi


java -Dfile.encoding=UTF-8 -Djava.library.path=. -cp Rukkit-0.9.4-dev.jar:libs/* cn.rukkit.RukkitLauncher
