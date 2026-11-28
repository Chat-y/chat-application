@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Chaty - Run Application in Android Emulator
echo ===================================================
echo.

:: 1. Check/Set Android SDK location
if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    )
)

:: Ensure local.properties exists
if not exist "local.properties" (
    echo [INFO] Creating local.properties with Android SDK path...
    if defined ANDROID_HOME (
        set "ESCAPED_SDK=%ANDROID_HOME:\=\\%"
        set "ESCAPED_SDK=!ESCAPED_SDK::=\:!"
        echo sdk.dir=!ESCAPED_SDK!> local.properties
    ) else (
        echo [WARNING] ANDROID_HOME not found. Make sure sdk.dir is defined in local.properties.
    )
)

:: Find emulator executable
set "EMULATOR_EXE="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\emulator\emulator.exe" (
    set "EMULATOR_EXE=%ANDROID_HOME%\emulator\emulator.exe"
)
if not defined EMULATOR_EXE (
    where emulator.exe >nul 2>&1
    if !errorlevel! equ 0 set "EMULATOR_EXE=emulator.exe"
)

:: Find adb executable
set "ADB_EXE="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"
)
if not defined ADB_EXE (
    where adb.exe >nul 2>&1
    if !errorlevel! equ 0 (
        set "ADB_EXE=adb.exe"
    ) else (
        echo [ERROR] adb.exe not found in PATH or Android SDK!
        pause
        exit /b 1
    )
)

:: 2. Check if an emulator/device is already running
set "DEVICE_RUNNING=0"
for /f "tokens=1,2" %%A in ('"%ADB_EXE%" devices') do (
    if "%%B"=="device" (
        set "DEVICE_RUNNING=1"
        set "DEVICE_ID=%%A"
    )
)

if "!DEVICE_RUNNING!"=="1" (
    echo [INFO] Found running Android device/emulator: !DEVICE_ID!
) else (
    echo [INFO] No running device detected. Starting emulator...
    if not defined EMULATOR_EXE (
        echo [ERROR] emulator.exe could not be located!
        pause
        exit /b 1
    )

    :: Detect default AVD
    set "AVD_NAME=Medium_Phone_API_37.0"
    for /f "tokens=*" %%i in ('"%EMULATOR_EXE%" -list-avds 2^>nul') do (
        if not defined FOUND_AVD (
            set "FOUND_AVD=%%i"
        )
        if "%%i"=="Medium_Phone_API_37.0" set "AVD_NAME=%%i"
    )
    if not defined AVD_NAME if defined FOUND_AVD set "AVD_NAME=!FOUND_AVD!"

    echo [INFO] Launching emulator AVD: !AVD_NAME!...
    start "" "!EMULATOR_EXE!" -avd "!AVD_NAME!"

    echo [INFO] Waiting for emulator to boot up. This may take a minute...
    "%ADB_EXE%" wait-for-device

    :WAIT_BOOT
    timeout /t 2 /nobreak >nul
    for /f %%A in ('"%ADB_EXE%" shell getprop sys.boot_completed 2^>nul') do (
        set "BOOT=%%A"
    )
    if not "!BOOT!"=="1" (
        echo [INFO] Still booting...
        goto WAIT_BOOT
    )
    echo [SUCCESS] Emulator is fully booted and ready!
)

echo.
echo ===================================================
echo   Building and Installing Chaty App
echo ===================================================
echo.

:: 3. Build Debug APK
echo [INFO] Building debug APK...
call gradle :app:assembleDebug -PskipMlsNative=true --no-daemon
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Gradle build failed!
    pause
    exit /b !errorlevel!
)

:: 4. Install onto Emulator
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at %APK_PATH%!
    pause
    exit /b 1
)

echo [INFO] Installing APK on emulator...
"%ADB_EXE%" install -r "%APK_PATH%"
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Failed to install APK on emulator!
    pause
    exit /b !errorlevel!
)

:: 5. Launch App
echo [INFO] Launching Chaty on emulator...
"%ADB_EXE%" shell am start -n com.logybyte.chaty/com.example.chat.MainActivity

echo.
echo ===================================================
echo   [SUCCESS] Chaty is now running on your emulator!
echo ===================================================
echo.
pause
