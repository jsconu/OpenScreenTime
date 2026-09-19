<#
.SYNOPSIS
  Creates the upload keystore for signing OpenScreenTime release builds and writes keystore.properties.

.DESCRIPTION
  Run this once, on your own machine:   powershell -ExecutionPolicy Bypass -File scripts\create-upload-keystore.ps1

  - You type the password here; it is never written to the screen, to a log, or to this repo.
  - Creates openscreentime-upload.jks and keystore.properties in the repo root. Both are gitignored.
  - Prints the SHA-1 and SHA-256 fingerprints to add to your Firebase apps.
  - Refuses to overwrite an existing keystore (losing the key means being unable to update the app).

  -CheckOnly: just find keytool and stop (nothing is created, no password is asked for).
#>
param(
    [switch]$CheckOnly,
    [string]$KeystoreName = "openscreentime-upload.jks",
    [string]$Alias = "upload",
    [string]$DistinguishedName = "CN=OpenScreenTime upload key, O=OpenScreenTime"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Find-Keytool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
    $candidates += "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe"
    $candidates += "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    $candidates += "$env:ProgramFiles\Android\Android Studio Preview\jbr\bin\keytool.exe"
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    return $null
}

$keytool = Find-Keytool
if (-not $keytool) {
    Write-Host "Could not find keytool. Install Android Studio (it ships with one), or set JAVA_HOME to a JDK 17." -ForegroundColor Red
    exit 1
}
Write-Host "Using keytool: $keytool"
if ($CheckOnly) { Write-Host "Check only - stopping here."; exit 0 }

$keystorePath = Join-Path $repoRoot $KeystoreName
$propsPath = Join-Path $repoRoot "keystore.properties"
if (Test-Path $keystorePath) {
    Write-Host "$KeystoreName already exists. Not overwriting it." -ForegroundColor Red
    exit 1
}
if (Test-Path $propsPath) {
    Write-Host "keystore.properties already exists. Not overwriting it - remove it first if you really mean to start over." -ForegroundColor Red
    exit 1
}

function Read-PlainPassword($prompt) {
    $secure = Read-Host -AsSecureString $prompt
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

$password = Read-PlainPassword "Choose a password for the keystore (at least 8 characters)"
$confirm = Read-PlainPassword "Type it again"
if ($password -ne $confirm) { Write-Host "Passwords didn't match. Nothing was created." -ForegroundColor Red; exit 1 }
if ($password.Length -lt 8) { Write-Host "Use at least 8 characters. Nothing was created." -ForegroundColor Red; exit 1 }

# The password reaches keytool through an environment variable (keytool's ":env" option), not the
# command line, so it doesn't show up in the process list.
$env:OST_KEYSTORE_PASSWORD_TMP = $password
try {
    & $keytool -genkeypair -keystore $keystorePath -alias $Alias -keyalg RSA -keysize 2048 -validity 10000 `
        -dname $DistinguishedName -storepass:env OST_KEYSTORE_PASSWORD_TMP -keypass:env OST_KEYSTORE_PASSWORD_TMP
    if ($LASTEXITCODE -ne 0) { throw "keytool failed (exit code $LASTEXITCODE)." }

    # keystore.properties is what kid/ and parent/ build.gradle.kts read (see docs/PUBLISHING.md).
    @(
        "storeFile=$KeystoreName",
        "storePassword=$password",
        "keyAlias=$Alias",
        "keyPassword=$password"
    ) | Set-Content -Path $propsPath -Encoding ASCII

    Write-Host ""
    Write-Host "Created $KeystoreName and keystore.properties (both are gitignored)." -ForegroundColor Green
    Write-Host ""
    Write-Host "Fingerprints to add to BOTH Android apps in the Firebase console (Project settings > Your apps):"
    & $keytool -list -v -keystore $keystorePath -alias $Alias -storepass:env OST_KEYSTORE_PASSWORD_TMP |
        Select-String -Pattern "SHA1:|SHA256:"
}
finally {
    Remove-Item Env:\OST_KEYSTORE_PASSWORD_TMP -ErrorAction SilentlyContinue
    $password = $null; $confirm = $null
}

Write-Host ""
Write-Host "NEXT: back up $KeystoreName and the password in TWO separate places (e.g. a password manager and an" -ForegroundColor Yellow
Write-Host "encrypted USB stick). If you lose them you can't update the app under the same key." -ForegroundColor Yellow
