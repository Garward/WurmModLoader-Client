@echo off
REM WurmModLoader Client Patcher (Windows)
REM
REM Bakes the modloader bootstrap + GUI access widenings into client.jar so
REM launching the game through Steam or WurmLauncher loads mods automatically.
REM Idempotent: restores from client.jar.backup before re-patching.

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

if exist "%SCRIPT_DIR%\client.jar" (
    set "CLIENT_DIR=%SCRIPT_DIR%"
) else (
    set "CLIENT_DIR=%ProgramFiles(x86)%\Steam\steamapps\common\Wurm Unlimited\WurmLauncher"
)

set "CLIENT_JAR=%CLIENT_DIR%\client.jar"
set "CLIENT_BACKUP=%CLIENT_DIR%\client.jar.backup"

echo ======================================================================
echo  WurmModLoader Client Patcher
echo ======================================================================
echo.

if not exist "%CLIENT_JAR%" (
    echo [ERROR] client.jar not found at: %CLIENT_JAR%
    echo         Place this script next to client.jar, or install Wurm via Steam.
    exit /b 1
)

set "MODLOADER_JAR="
for /f "delims=" %%f in ('dir /b /o-d "%CLIENT_DIR%\wurmmodloader-client-*.jar" 2^>nul') do (
    if not defined MODLOADER_JAR set "MODLOADER_JAR=%CLIENT_DIR%\%%f"
)
if not defined MODLOADER_JAR (
    echo [ERROR] No wurmmodloader-client-*.jar found in %CLIENT_DIR%
    exit /b 1
)

set "WURM_JRE=%CLIENT_DIR%\..\runtime\jre1.8.0_172\bin\java.exe"
if not exist "%WURM_JRE%" set "WURM_JRE=java"

echo Client dir: %CLIENT_DIR%
echo Modloader:  %MODLOADER_JAR%
echo Java:       %WURM_JRE%
echo.

if exist "%CLIENT_BACKUP%" (
    echo Restoring vanilla client.jar from backup before re-patch...
    copy /y "%CLIENT_BACKUP%" "%CLIENT_JAR%" >nul
)

echo Patching...
echo.

pushd "%CLIENT_DIR%"
"%WURM_JRE%" -jar "%MODLOADER_JAR%"
set "PATCH_EXIT=%errorlevel%"
popd

if "%PATCH_EXIT%"=="0" (
    echo.
    echo ======================================================================
    echo  client.jar patched successfully
    echo ======================================================================
    echo   Backup: %CLIENT_BACKUP%
    echo   To restore vanilla: move "%CLIENT_BACKUP%" "%CLIENT_JAR%"
    echo.
) else (
    echo.
    echo [ERROR] Patch failed. See output above.
    exit /b 1
)
