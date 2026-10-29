$adbCandidates = @(
    "adb",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "C:\Users\jayan\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    "F:\downlods\platform-tools-latest-windows\platform-tools\adb.exe"
)

$adb = $null
foreach ($candidate in $adbCandidates) {
    if (Get-Command $candidate -ErrorAction SilentlyContinue) {
        $adb = $candidate
        break
    } elseif (Test-Path $candidate) {
        $adb = $candidate
        break
    }
}

if (-not $adb) {
    Write-Host "Error: ADB binary not found in standard paths or PATH." -ForegroundColor Red
    exit 1
}

Write-Host "Using ADB: $adb" -ForegroundColor Cyan
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    Write-Host "Error: APK not found at $apk. Please run './gradlew.bat assembleDebug' first." -ForegroundColor Red
    exit 1
}

Write-Host "Checking for connected Android devices..." -ForegroundColor Cyan
$devicesOutput = & $adb devices -l
Write-Host $devicesOutput

$lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -and -not ($_ -match "List of devices attached") -and -not ($_ -match "\* daemon") }
$activeDevices = @()
$unauthorizedDevices = @()

foreach ($line in $lines) {
    if ($line -match "^\s*([^\s]+)\s+device") {
        $activeDevices += $matches[1]
    } elseif ($line -match "^\s*([^\s]+)\s+unauthorized") {
        $unauthorizedDevices += $matches[1]
    }
}

if ($unauthorizedDevices.Count -gt 0) {
    Write-Host "`nWARNING: Device $($unauthorizedDevices -join ', ') is UNAUTHORIZED." -ForegroundColor Yellow
    Write-Host "Please unlock your phone and tap 'Allow USB Debugging' (check 'Always allow from this computer')." -ForegroundColor Yellow
}

if ($activeDevices.Count -eq 0) {
    Write-Host "`nNo authorized devices currently connected." -ForegroundColor Yellow
    Write-Host "Waiting for device (ensure USB Debugging is ON in Developer Options, USB mode is 'File Transfer', and phone is unlocked)..." -ForegroundColor Yellow
    & $adb wait-for-device
    $activeDevices = @((& $adb get-serialno).Trim())
}

$targetDevice = $activeDevices[0]
Write-Host "`nTarget Device Serial: $targetDevice" -ForegroundColor Green

# Query Device Properties for Compatibility Check
$deviceModel = (& $adb -s $targetDevice shell getprop ro.product.model).Trim()
$deviceApi = (& $adb -s $targetDevice shell getprop ro.build.version.sdk).Trim()
$deviceAbi = (& $adb -s $targetDevice shell getprop ro.product.cpu.abi).Trim()

Write-Host "Device Model: $deviceModel | API Level: $deviceApi | ABI: $deviceAbi" -ForegroundColor Cyan

if ([int]$deviceApi -lt 29) {
    Write-Host "`nError: Connected device is running Android API $deviceApi. ResQhunT requires minSdk 29 (Android 10.0+)." -ForegroundColor Red
    exit 1
}

Write-Host "`nInstalling ResQhunT debug APK on device $targetDevice..." -ForegroundColor Cyan
$installResult = (& $adb -s $targetDevice install -r -d $apk 2>&1)
$installOutput = $installResult -join "`n"
Write-Host $installOutput

if ($LASTEXITCODE -eq 0 -and $installOutput -match "Success") {
    Write-Host "`nSuccessfully installed ResQhunT!" -ForegroundColor Green
    Write-Host "Launching app on device..." -ForegroundColor Cyan
    & $adb -s $targetDevice shell monkey -p com.resqhunt.citizen -c android.intent.category.LAUNCHER 1
} else {
    Write-Host "`nInstallation Failed. Diagnosing cause..." -ForegroundColor Red

    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "`nDIAGNOSIS: SIGNATURE CERTIFICATE CONFLICT (INSTALL_FAILED_UPDATE_INCOMPATIBLE)" -ForegroundColor Yellow
        Write-Host "The device already has a version of ResQhunT installed that was signed with a different key (e.g. from another PC or a release build)." -ForegroundColor Yellow
        Write-Host "WARNING: Uninstalling will erase all local SOS records and mesh buffers stored on the device." -ForegroundColor Red
        
        $choice = Read-Host "Would you like to uninstall the older version and reinstall the new APK? (y/N)"
        if ($choice -match "^[yY]") {
            Write-Host "Uninstalling older version..." -ForegroundColor Yellow
            & $adb -s $targetDevice uninstall com.resqhunt.citizen
            Write-Host "Retrying installation..." -ForegroundColor Cyan
            & $adb -s $targetDevice install $apk
            if ($LASTEXITCODE -eq 0) {
                Write-Host "`nSuccessfully installed ResQhunT after clean reinstall!" -ForegroundColor Green
                & $adb -s $targetDevice shell monkey -p com.resqhunt.citizen -c android.intent.category.LAUNCHER 1
            }
        }
    } elseif ($installOutput -match "INSTALL_FAILED_OLDER_SDK") {
        Write-Host "`nDIAGNOSIS: INCOMPATIBLE ANDROID VERSION (INSTALL_FAILED_OLDER_SDK)" -ForegroundColor Yellow
        Write-Host "Device Android version does not meet minSdk 26 requirement." -ForegroundColor Red
    } elseif ($installOutput -match "INSTALL_FAILED_INSUFFICIENT_STORAGE") {
        Write-Host "`nDIAGNOSIS: INSUFFICIENT STORAGE (INSTALL_FAILED_INSUFFICIENT_STORAGE)" -ForegroundColor Yellow
        Write-Host "Please free up storage space on the Android device and retry." -ForegroundColor Red
    } elseif ($installOutput -match "INSTALL_FAILED_USER_RESTRICTED" -or $installOutput -match "INSTALL_CANCELED_BY_USER") {
        Write-Host "`nDIAGNOSIS: INSTALL BLOCKED BY SYSTEM / USER" -ForegroundColor Yellow
        Write-Host "Please check your phone screen to accept the installation prompt, or disable 'Verify apps over USB' in Developer Options." -ForegroundColor Yellow
    } else {
        Write-Host "`nUnknown installation failure: $installOutput" -ForegroundColor Red
    }
}
