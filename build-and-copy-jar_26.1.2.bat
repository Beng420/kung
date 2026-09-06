@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
set "MOD_VERSION=0.2.1"
set "SOURCE_JAR=%ROOT%versions\mc26_1_2\build\libs\kung-26.1.2-%MOD_VERSION%.jar"
set "DEFAULT_PROFILE=Dungeons 26.1.2"
set "PROFILE_NAME=%~1"
if "%PROFILE_NAME%"=="" set "PROFILE_NAME=%DEFAULT_PROFILE%"
set "PROFILE_DIR=%APPDATA%\ModrinthApp\profiles\%PROFILE_NAME%"
set "SCAN_DIR=%APPDATA%\ModrinthApp\profiles\%DEFAULT_PROFILE%\kung-dungeon-scans"
set "RESOURCE_SCAN_DIR=%ROOT%versions\mc26_1_2\src\main\resources\kung-dungeon-scans"

echo Syncing learned room data into the jar resources...
if not exist "!RESOURCE_SCAN_DIR!" mkdir "!RESOURCE_SCAN_DIR!"

if exist "!SCAN_DIR!\known-rooms.json" (
    copy /Y "!SCAN_DIR!\known-rooms.json" "!RESOURCE_SCAN_DIR!\known-rooms.json" >nul
) else if exist "!SCAN_DIR!\known-rooms.jsonl" (
    copy /Y "!SCAN_DIR!\known-rooms.jsonl" "!RESOURCE_SCAN_DIR!\known-rooms.jsonl" >nul
) else (
    echo No known-rooms.json or known-rooms.jsonl found yet.
)

if exist "!SCAN_DIR!\known-room-preloads.jsonl" (
    copy /Y "!SCAN_DIR!\known-room-preloads.jsonl" "!RESOURCE_SCAN_DIR!\known-room-preloads.jsonl" >nul
)

if exist "!SCAN_DIR!\known-room-types.properties" (
    copy /Y "!SCAN_DIR!\known-room-types.properties" "!RESOURCE_SCAN_DIR!\known-room-types.properties" >nul
)

echo Building kung jar...
pushd "!ROOT!" >nul
call "!ROOT!gradlew.bat" :versions:mc26_1_2:build -PkungPrivateRoomSyncDefaults=true
set "BUILD_RESULT=%ERRORLEVEL%"
popd >nul

if not "!BUILD_RESULT!"=="0" (
    echo.
    echo Build failed. Jar was not copied.
    pause
    exit /b !BUILD_RESULT!
)

if not exist "!SOURCE_JAR!" (
    echo.
    echo Built jar was not found:
    echo !SOURCE_JAR!
    pause
    exit /b 1
)

tasklist /FI "IMAGENAME eq javaw.exe" 2>nul | find /I "javaw.exe" >nul
if not errorlevel 1 (
    echo.
    echo Minecraft/Java is still running. Close all Minecraft windows before copying the jar.
    echo Built jar was left here:
    echo !SOURCE_JAR!
    pause
    exit /b 1
)

echo.
if "%~1"=="" (
    call :copyProfile "%PROFILE_NAME%"
    goto copied_all
)

:copy_loop
if "%~1"=="" goto copied_all
call :copyProfile "%~1"
if errorlevel 1 exit /b 1
shift
goto copy_loop

:copied_all
echo.
echo Done.
echo.
certutil -hashfile "!SOURCE_JAR!" SHA256
pause
exit /b 0

:copyProfile
set "TARGET_PROFILE=%~1"
set "TARGET_DIR=%APPDATA%\ModrinthApp\profiles\%TARGET_PROFILE%\mods"
set "TARGET_JAR=%TARGET_DIR%\kung-26.1.2-%MOD_VERSION%.jar"

if not exist "!TARGET_DIR!" (
    echo Creating mods folder:
    echo !TARGET_DIR!
    mkdir "!TARGET_DIR!"
)

echo Copying jar to Modrinth profile: !TARGET_PROFILE!
copy /Y "!SOURCE_JAR!" "!TARGET_JAR!" >nul
if errorlevel 1 (
    echo.
    echo Copy failed:
    echo !TARGET_JAR!
    pause
    exit /b 1
)
echo !TARGET_JAR!
exit /b 0
