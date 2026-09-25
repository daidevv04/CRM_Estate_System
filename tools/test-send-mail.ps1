# Gui thu mot email that qua duong OTP de kiem tra Resend + mail-service.
#   .\tools\test-send-mail.ps1 -Email trananhdai.itqnu@gmail.com
#
# Cach lam: tao user tam voi email nhan, goi /api/auth/otp/send (user-service se goi
# mail-service -> Resend), in ket qua, roi xoa user tam. Neu Resend tra 4xx thi xem
# logs\mail-service.log (dong "Resend rejected ...").
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$token = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'make-token.ps1') | Select-Object -Last 1).Trim()
$headers = @{ Authorization = "Bearer $token" }
$username = "mailtest$stamp"

function Post([string]$path, $body) {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl$path" -Headers $headers -ContentType 'application/json' `
        -Body ($body | ConvertTo-Json -Compress) -TimeoutSec 30 -UseBasicParsing
}

$user = Post '/api/users' @{
    username = $username
    password = 'MailTest123!'
    email    = $Email
    fullName = 'Mail test'
    role     = 'SALES'
}
"user tam: $($user.id) ($username, email=$Email)"

try {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/otp/send" -Headers $headers -ContentType 'application/json' `
        -Body (@{ usernameOrEmail = $Email; purpose = 'RESET_PASSWORD' } | ConvertTo-Json -Compress) -TimeoutSec 30 -UseBasicParsing | Out-Null
    'OTP send: 204 - Resend DA CHAP NHAN (kiem tra hop thu)'
} catch {
    "OTP send THAT BAI: $($_.Exception.Message)"
    "chi tiet trong logs\mail-service.log (dong 'Resend rejected ...')"
} finally {
    try {
        Invoke-RestMethod -Method DELETE -Uri "$BaseUrl/api/users/$($user.id)" -Headers $headers -TimeoutSec 20 -UseBasicParsing | Out-Null
        "da xoa user tam"
    } catch {
        "khong xoa duoc user tam: $($_.Exception.Message)"
    }
}
