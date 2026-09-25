# Chay test-api.ps1 bang token ky san, ghi ket qua ra logs\test-run.txt.
# Dung khi can chay dai / xem lai ket qua sau.
#   .\tools\run-tests.ps1 -TestEmail ban@gmail.com   # test them luong gui OTP that
param([string]$TestEmail = '')

$root = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force $logDir | Out-Null

$token = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'make-token.ps1') | Select-Object -Last 1).Trim()

$extra = if ($TestEmail) { @('-TestEmail', $TestEmail) } else { @() }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'test-api.ps1') -AccessToken $token @extra *> (Join-Path $logDir 'test-run.txt')
"DONE exit=$LASTEXITCODE" | Add-Content (Join-Path $logDir 'test-run.txt')
Get-Content (Join-Path $logDir 'test-run.txt') | Select-Object -Last 5
