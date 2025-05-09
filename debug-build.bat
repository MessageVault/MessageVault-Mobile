@echo off
echo Cleaning project...
call gradlew clean

echo 检查Kotlin和Compose版本兼容性...
call gradlew -q dependencyInsight --dependency kotlin-stdlib

echo Running full debug build with detailed logs...
call gradlew assembleDebug --stacktrace --info --scan

echo If build fails, check the logs above for detailed error information.
