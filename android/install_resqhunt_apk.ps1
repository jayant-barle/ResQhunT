$adb = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } elseif (Test-Path "F:\downlods\platform-tools-latest-windows\platform-tools\adb.exe") { "F:\downlods\platform-tools-latest-windows\platform-tools\adb.exe" } else { "adb" }
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    Write-Host "Error: APK not found at $apk" -ForegroundColor Red
    exit 1
}

Write-Host "Checking for connected Android devices..." -ForegroundColor Cyan
& $adb devices -l

Write-Host "`nWaiting for device (ensure USB Debugging is ON and phone is unlocked)..." -ForegroundColor Yellow
& $adb wait-for-device

Write-Host "`nDevice detected! Installing ResQhunT APK..." -ForegroundColor Green
& $adb install -r -d $apk

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSuccessfully installed ResQhunT!" -ForegroundColor Green
    Write-Host "Launching app on device..." -ForegroundColor Cyan
    & $adb shell monkey -p com.resqhunt.citizen -c android.intent.category.LAUNCHER 1
} else {
    Write-Host "`nInstallation failed. Check your phone screen to approve the installation prompt if required." -ForegroundColor Red
}
