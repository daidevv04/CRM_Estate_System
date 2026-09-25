<#
dev-up.ps1 - Chay stack local bang mvnw (Consul chay rieng ngoai).

  .\dev-up.ps1 -DryRun              # chi in ra se chay gi (kiem tra .env), khong start
  .\dev-up.ps1                      # start config-service truoc, cho healthy, roi 5 service con lai
  .\dev-up.ps1 -Only user-service    # chi start 1 service (cac service khac da chay san)
  .\dev-up.ps1 -Background           # chay nen, log vao logs\<service>.log (de kiem tra tu xa)

Dieu kien:
  - Java 17 trong PATH. Khong can cai Maven: moi module co mvnw (Maven wrapper).
  - Consul dang chay:  consul agent -dev      (http://localhost:8500)
    Dang ky Consul that bai lam service chet ngay luc start, nen Consul phai len truoc.
  - .env da dien day du (DB Supabase, JWT_SECRET, DUMMY_BCRYPT_HASH, RESEND_API_KEY, MAIL_FROM).

Thu tu: config-service -> user/customer/crm/mail -> api-gateway-service
  (config client chi fetch config 1 lan luc start; route lb:// can instance da dang ky trong Consul)
Moi service mo 1 cua so PowerShell rieng: dong cua so do la dung service.
#>
param(
    [switch]$DryRun,
    [string]$Only,
    [switch]$Background
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$order = @('config-service', 'user-service', 'customer-service', 'crm-service', 'mail-service', 'api-gateway-service')

$portSetting = @{
    'config-service'      = 'CONFIG_SERVICE_PORT'
    'user-service'        = 'USER_SERVICE_PORT'
    'customer-service'    = 'CUSTOMER_SERVICE_PORT'
    'crm-service'         = 'CRM_SERVICE_PORT'
    'mail-service'        = 'MAIL_SERVICE_PORT'
    'api-gateway-service' = 'GATEWAY_PORT'
}

# Bo co JVM cho 6 tien trinh tren mot may.
$javaFlags = '-Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k'

$envFile = Join-Path $root '.env'
if (-not (Test-Path $envFile)) { throw ".env khong ton tai: copy .env.example thanh .env roi dien gia tri" }

# Doc .env: bo comment/dong trong, giu nguyen gia tri.
$settings = [ordered]@{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}

function Get-Setting([string]$name, [string]$fallback) {
    if ($settings.Contains($name) -and $settings[$name] -ne '') { return $settings[$name] }
    return $fallback
}

$consulHost = Get-Setting 'SPRING_CLOUD_CONSUL_HOST' 'localhost'
$consulPort = Get-Setting 'SPRING_CLOUD_CONSUL_PORT' '8500'
try {
    # -UseBasicParsing: khong co thi Windows PowerShell hoi "Security Warning" va chan script.
    Invoke-WebRequest -Uri "http://${consulHost}:${consulPort}/v1/status/leader" -TimeoutSec 3 -UseBasicParsing | Out-Null
    $consulUp = $true
} catch {
    $consulUp = $false
}

$configUrl = 'http://localhost:' + (Get-Setting 'CONFIG_SERVICE_PORT' '8888')

$plan = @()
foreach ($service in $order) {
    if ($Only -and $service -ne $Only) { continue }
    $port = Get-Setting $portSetting[$service] $null
    if (-not $port) { throw "Thieu bien $($portSetting[$service]) trong .env" }
    $module = Join-Path $root "services\$service"
    if (-not (Test-Path (Join-Path $module 'mvnw.cmd'))) { throw "$module thieu mvnw.cmd (Maven wrapper)" }
    $plan += [pscustomobject]@{ Service = $service; Port = [int]$port; Module = $module }
}

Write-Host "Consul : $(if ($consulUp) { "OK ($consulHost`:$consulPort)" } else { "CHUA CHAY -> chay: consul agent -dev" })"
Write-Host "Config : $configUrl"
$plan | ForEach-Object { Write-Host ("{0,-20} port {1}  {2}" -f $_.Service, $_.Port, $_.Module) }

if ($DryRun) { Write-Host "`nDryRun: khong start gi."; return }
if (-not $consulUp) { throw "Consul chua chay ($consulHost`:$consulPort). Start bang: consul agent -dev" }

function Start-EstateService($item) {
    $assignments = ($settings.Keys | ForEach-Object {
        '$env:' + $_ + " = '" + ($settings[$_] -replace "'", "''") + "'"
    }) -join '; '
    $command = $assignments +
        "; `$env:SERVER_PORT = '$($item.Port)'" +
        "; `$env:JAVA_TOOL_OPTIONS = '$javaFlags'" +
        # Toan he thong luu UTC (xem DATABASE_DESIGN_V2: JVM chay TZ=UTC, DB session dat UTC).
        "; `$env:TZ = 'UTC'" +
        "; `$Host.UI.RawUI.WindowTitle = '$($item.Service)'" +
        "; Set-Location '$($item.Module)'" +
        "; .\mvnw.cmd spring-boot:run"
    if ($Background) {
        # Chay nen: log ra logs\<service>.log de doc lai duoc, khong mo cua so.
        $logDir = Join-Path $root 'logs'
        New-Item -ItemType Directory -Force $logDir | Out-Null
        Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-Command', $command `
            -RedirectStandardOutput (Join-Path $logDir "$($item.Service).log") `
            -RedirectStandardError (Join-Path $logDir "$($item.Service).err.log") `
            -WindowStyle Hidden | Out-Null
    } else {
        Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoExit', '-NoProfile', '-Command', $command
    }
}

$configService = $plan | Where-Object { $_.Service -eq 'config-service' }
if ($configService) {
    Start-EstateService $configService
    Write-Host "`nconfig-service dang khoi dong, cho /actuator/health..."
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
        (Get-Setting 'CONFIG_SERVER_USERNAME' 'config-admin') + ':' + (Get-Setting 'CONFIG_SERVER_PASSWORD' '')))
    $deadline = (Get-Date).AddSeconds(120)
    $healthy = $false
    do {
        Start-Sleep -Seconds 3
        try {
            $healthy = (Invoke-WebRequest -Uri "$configUrl/actuator/health" -Headers @{ Authorization = "Basic $basic" } -TimeoutSec 3 -UseBasicParsing).StatusCode -eq 200
        } catch {
            $healthy = $false
        }
    } until ($healthy -or (Get-Date) -gt $deadline)
    if ($healthy) {
        Write-Host 'config-service: healthy'
    } else {
        Write-Host 'config-service: chua healthy sau 120s - van start tiep (service se dung gia tri local)'
    }
}

foreach ($item in ($plan | Where-Object { $_.Service -ne 'config-service' })) {
    Start-EstateService $item
}
Write-Host "`nDa start $($plan.Count) service. Gateway: http://localhost:$((Get-Setting 'GATEWAY_PORT' '8080'))/actuator/health"
