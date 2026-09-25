# Ky access token HS256 bang JWT_SECRET trong .env, dung cho test-api.ps1.
#   $token = & .\tools\make-token.ps1
#   .\test-api.ps1 -AccessToken $token
#
# Mac dinh la tai khoan admin trong bang users (lay id bang tools/query-userdb.ps1).
param(
    [string]$UserId = '5cdd2737-4102-461f-80ad-9bf58e917fc4',
    [string]$Username = 'admin',
    [string]$Role = 'ADMIN',
    [int]$TtlSeconds = 900
)

$root = Split-Path $PSScriptRoot -Parent
$settings = @{}
Get-Content (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}

& "$env:JAVA_HOME\bin\java.exe" (Join-Path $PSScriptRoot 'MakeToken.java') `
    $settings['JWT_SECRET'] $UserId $Username $Role $TtlSeconds
