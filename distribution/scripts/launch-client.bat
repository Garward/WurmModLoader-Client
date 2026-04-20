@echo off
REM WurmModLoader Client Launcher (Windows)
REM
REM This script launches the Wurm Unlimited client with the modloader agent.
REM

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MODLOADER_JAR=%SCRIPT_DIR%wurmmodloader-client-0.1.0.jar"

REM Wurm installation paths
set "WURM_DIR=%PROGRAMFILES(X86)%\Steam\steamapps\common\Wurm Unlimited"
set "WURM_JRE=%WURM_DIR%\runtime\jre1.8.0_172\bin\java.exe"
set "CLIENT_DIR=%WURM_DIR%\WurmLauncher"
set "CLIENT_JAR=%CLIENT_DIR%\client.jar"
set "COMMON_JAR=%CLIENT_DIR%\common.jar"
set "NATIVE_LIBS=%CLIENT_DIR%\nativelibs"

REM Validate paths
if not exist "%MODLOADER_JAR%" (
    echo Error: ModLoader JAR not found at: %MODLOADER_JAR%
    exit /b 1
)

if not exist "%WURM_JRE%" (
    echo Error: Wurm JRE not found at: %WURM_JRE%
    echo Make sure Wurm Unlimited is installed via Steam
    exit /b 1
)

if not exist "%CLIENT_JAR%" (
    echo Error: Client JAR not found at: %CLIENT_JAR%
    exit /b 1
)

echo === WurmModLoader Client Launcher ===
echo ModLoader:  %MODLOADER_JAR%
echo Client JAR: %CLIENT_JAR%
echo Java:       %WURM_JRE%
echo.

REM Launch the client with the modloader as a Java agent
REM Using Wurm's bundled JRE which includes JavaFX
"%WURM_JRE%" -javaagent:"%MODLOADER_JAR%" -Djava.library.path="%NATIVE_LIBS%" -cp "%CLIENT_JAR%;%COMMON_JAR%" com.wurmonline.client.launcherfx.WurmMain %*
