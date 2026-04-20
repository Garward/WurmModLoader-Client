@echo off
REM WurmModLoader Client Installer (Windows)
REM Patches client.jar to automatically load mods when the game starts.

setlocal enabledelayedexpansion

echo =======================================================================
echo  WurmModLoader Client Installer
echo =======================================================================
echo.

if defined WURM_CLIENT_DIR (
    set "WURM_LAUNCHER=%WURM_CLIENT_DIR%"
) else (
    set "WURM_LAUNCHER=C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher"
)

set "CLIENT_JAR=%WURM_LAUNCHER%\client.jar"

if not exist "%CLIENT_JAR%" (
    echo [ERROR] Could not find Wurm Unlimited installation.
    echo   Expected: %CLIENT_JAR%
    echo.
    echo   Make sure Wurm Unlimited is installed via Steam, or set
    echo   WURM_CLIENT_DIR to the WurmLauncher directory.
    exit /b 1
)

echo Found Wurm Unlimited installation
echo   Location: %WURM_LAUNCHER%
echo.

if exist "%WURM_LAUNCHER%\client.jar.backup" (
    echo [WARNING] Client appears to be already patched (backup exists).
    set /p REPLY="Re-patch anyway? This will restore from backup first. (y/N) "
    if /i not "!REPLY!"=="y" (
        echo Installation cancelled.
        exit /b 0
    )
    echo Restoring from backup...
    copy /y "%WURM_LAUNCHER%\client.jar.backup" "%CLIENT_JAR%" >nul
    del "%WURM_LAUNCHER%\client.jar.backup"
)

echo Building WurmModLoader...
call gradlew.bat clean build dist -q
if errorlevel 1 (
    echo [ERROR] Build failed.
    exit /b 1
)
echo Build complete.
echo.

echo Deploying to Wurm Unlimited...
set "DIST_ZIP="
for /f "delims=" %%f in ('dir /b /o-d "build\distributions\WurmModloader-Client-*.zip" 2^>nul') do (
    if not defined DIST_ZIP set "DIST_ZIP=build\distributions\%%f"
)

set "TEMP_DIR=%TEMP%\wurmmodloader-install-%RANDOM%"
mkdir "%TEMP_DIR%" >nul 2>&1
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%DIST_ZIP%' -DestinationPath '%TEMP_DIR%' -Force"

copy /y "%TEMP_DIR%\wurmmodloader-client-*.jar" "%WURM_LAUNCHER%\" >nul
set "MODLOADER_JAR="
for /f "delims=" %%f in ('dir /b /o-d "%WURM_LAUNCHER%\wurmmodloader-client-*.jar"') do (
    if not defined MODLOADER_JAR set "MODLOADER_JAR=%WURM_LAUNCHER%\%%f"
)
echo Copied modloader: %MODLOADER_JAR%

rmdir /s /q "%TEMP_DIR%"

echo.
echo Patching client.jar...
echo.
java -jar "%MODLOADER_JAR%"
if errorlevel 1 (
    echo [ERROR] Patcher failed.
    exit /b 1
)

echo.
echo =======================================================================
echo  Installation Complete!
echo =======================================================================
echo.
echo The Wurm Unlimited client is now patched to load mods automatically.
echo.
echo Next Steps:
echo   1. Place mod JARs in: %WURM_LAUNCHER%\mods\
echo   2. Launch Wurm Unlimited through Steam normally
echo   3. Mods will load automatically at startup
echo.
echo To uninstall:
echo   cd "%WURM_LAUNCHER%"
echo   move /y client.jar.backup client.jar
echo   del wurmmodloader-client-*.jar
exit /b 0
