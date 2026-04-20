@echo off
REM WurmModLoader Client Build Script (Windows)

setlocal

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

echo ======================================================================
echo  WurmModLoader Client Build Script
echo ======================================================================
echo.

cd /d "%PROJECT_DIR%"

echo Project Directory: %PROJECT_DIR%
echo Gradle Version:
call gradlew.bat --version | findstr /R "Gradle JVM"
echo.

echo ======================================================================
echo  Running: gradlew.bat clean build dist
echo ======================================================================
echo.

call gradlew.bat clean build dist
if errorlevel 1 (
    echo.
    echo Build Failed!
    exit /b 1
)

echo.
echo ======================================================================
echo  Build Successful!
echo ======================================================================
echo.

echo Distribution:
dir /b build\distributions\*.zip 2>nul
echo.

echo Uber-JAR:
for %%f in (wurmmodloader-client-patcher\build\libs\wurmmodloader-client-*.jar) do (
    echo %%f | findstr /v "sources javadoc" >nul && echo %%f
)
echo.

echo Ready to deploy! Run: deploy.bat
exit /b 0
