#!/bin/bash

# 清理编译生成的文件
echo "清理编译生成的文件..."
./gradlew clean

# 删除调试和分析文件
echo "删除调试和分析文件..."
find . -name "*.hprof" -type f -delete
find . -name "java_pid*.log" -type f -delete
find . -name "hs_err_pid*.log" -type f -delete

# 删除其他不需要的临时文件
echo "删除临时文件..."
find . -name "*.tmp" -type f -delete
find . -name "*.bak" -type f -delete
find . -name "*~" -type f -delete

echo "清理完成!"
