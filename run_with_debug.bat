@echo off
echo Running gradle with debug information...
gradlew clean build --stacktrace --info --debug > build_debug_log.txt
echo Build completed. Check build_debug_log.txt for detailed information.
