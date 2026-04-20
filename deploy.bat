@echo off
REM WurmModLoader Client Smart Deploy (Windows)
REM Deploys client patcher to Wurm client directory (only changed files)

setlocal enabledelayedexpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

if defined WURM_CLIENT_DIR (
    set "CLIENT_DIR=%WURM_CLIENT_DIR%"
) else (
    set "CLIENT_DIR=C:\Program Files (x86)\Steam\steamapps\common\Wurm Unlimited\WurmLauncher"
)

set /a COPIED=0
set /a SKIPPED=0
set /a ERRORS=0

echo ======================================================================
echo  WurmModLoader Client Smart Deploy
echo ======================================================================
echo.

set "DIST_ZIP="
for /f "delims=" %%f in ('dir /b /o-d "%PROJECT_DIR%\build\distributions\WurmModloader-Client-*.zip" 2^>nul') do (
    if not defined DIST_ZIP set "DIST_ZIP=%PROJECT_DIR%\build\distributions\%%f"
)

if not defined DIST_ZIP (
    echo [ERROR] Distribution ZIP not found.
    echo Expected location: %PROJECT_DIR%\build\distributions\
    echo Run build first:   build.bat
    exit /b 1
)

if not exist "%CLIENT_DIR%" (
    echo [ERROR] Client directory not found: %CLIENT_DIR%
    echo Set WURM_CLIENT_DIR to override (or install Wurm Unlimited via Steam).
    exit /b 1
)

echo Distribution: %DIST_ZIP%
echo Deploy Dir:   %CLIENT_DIR%
echo.

set "TEMP_EXTRACT=%TEMP%\wurmmodloader-client-deploy-%RANDOM%"
mkdir "%TEMP_EXTRACT%" >nul 2>&1

echo Extracting distribution to temp...
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%DIST_ZIP%' -DestinationPath '%TEMP_EXTRACT%' -Force"
if errorlevel 1 (
    echo [ERROR] Failed to extract distribution.
    exit /b 1
)
echo OK
echo.

if not exist "%CLIENT_DIR%" mkdir "%CLIENT_DIR%"

echo ======================================================================
echo  Deploying Client Patcher
echo ======================================================================
for %%f in ("%TEMP_EXTRACT%\*.jar") do (
    call :copy_if_changed "%%f" "%CLIENT_DIR%\%%~nxf" "Patcher: %%~nxf"
)
echo.

echo ======================================================================
echo  Deploying Launcher Scripts
echo ======================================================================
if exist "%TEMP_EXTRACT%\scripts" (
    for %%f in ("%TEMP_EXTRACT%\scripts\*") do (
        call :copy_if_changed "%%f" "%CLIENT_DIR%\%%~nxf" "Script: %%~nxf"
    )
)
echo.

echo ======================================================================
echo  Deploying Documentation
echo ======================================================================
for %%f in ("%TEMP_EXTRACT%\*.md") do (
    call :copy_if_changed "%%f" "%CLIENT_DIR%\%%~nxf" "Docs: %%~nxf"
)
echo.

echo Cleaning up...
rmdir /s /q "%TEMP_EXTRACT%"
echo.

echo ======================================================================
echo  Deployment Summary
echo ======================================================================
echo  Copied:    %COPIED% files
echo  Unchanged: %SKIPPED% files
if %ERRORS% gtr 0 (
    echo  Errors:    %ERRORS%
    exit /b 1
)
echo.
echo Deployment Complete!
echo Installation Location: %CLIENT_DIR%\
echo.
echo Next steps:
echo   1. Launch the client (e.g. launch-client.bat in %CLIENT_DIR%)
echo   2. Watch for patcher output in client console
exit /b 0

:copy_if_changed
set "SRC=%~1"
set "DEST=%~2"
set "DESC=%~3"
if not exist "%SRC%" (
    echo  [X] %DESC% - source not found
    set /a ERRORS+=1
    goto :eof
)
if exist "%DEST%" (
    fc /b "%SRC%" "%DEST%" >nul 2>&1
    if not errorlevel 1 (
        echo  [.] %DESC% - unchanged
        set /a SKIPPED+=1
        goto :eof
    )
)
for %%P in ("%DEST%") do if not exist "%%~dpP" mkdir "%%~dpP" >nul 2>&1
copy /y "%SRC%" "%DEST%" >nul
if errorlevel 1 (
    echo  [X] %DESC% - copy failed
    set /a ERRORS+=1
) else (
    echo  [+] %DESC%
    set /a COPIED+=1
)
goto :eof
