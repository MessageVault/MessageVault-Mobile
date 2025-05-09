@echo off
echo === 检查环境配置 ===
echo.

echo ### Java 版本信息 ###
java -version
echo.

echo ### Gradle 版本信息 ###
gradlew --version
echo.

echo === 检查完成 ===
echo 如需详细构建日志，请运行 run_with_debug.bat
pause
