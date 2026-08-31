$ErrorActionPreference = "Stop"
$Version = "9.5.0"
$Expected = "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
$Dest = Join-Path $PSScriptRoot "gradle\wrapper\gradle-wrapper.jar"
$Url = "https://raw.githubusercontent.com/gradle/gradle/v$Version/gradle/wrapper/gradle-wrapper.jar"

New-Item -ItemType Directory -Force -Path (Split-Path $Dest) | Out-Null

if (Test-Path $Dest) {
    $Actual = (Get-FileHash $Dest -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Actual -eq $Expected) {
        Write-Host "Gradle Wrapper JAR $Version is already present and verified."
        exit 0
    }
    Write-Warning "Existing wrapper JAR has a wrong checksum; replacing it."
}

$Temp = "$Dest.tmp"
Remove-Item -Force -ErrorAction SilentlyContinue $Temp
Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Temp
$Actual = (Get-FileHash $Temp -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) {
    Remove-Item -Force $Temp
    throw "Gradle Wrapper checksum mismatch. Expected $Expected, got $Actual"
}
Move-Item -Force $Temp $Dest
Write-Host "Installed and verified Gradle Wrapper JAR $Version."
