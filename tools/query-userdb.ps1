# Chay SELECT tren DB user (Supabase) bang JDBC driver co san trong ~/.m2.
#   .\tools\query-userdb.ps1
#   .\tools\query-userdb.ps1 "select id, username, role from users"
# Mat khau lay tu .env va truyen qua bien moi truong (khong hien tren command line).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

$settings = @{}
Get-Content (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}

$driver = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql" -Recurse -Filter 'postgresql-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName

$env:DB_URL = "jdbc:postgresql://$($settings['USER_DB_HOST']):$($settings['USER_DB_PORT'])/$($settings['USER_DB_NAME'])?sslmode=require"
$env:DB_USER = $settings['USER_DB_USERNAME']
$env:DB_PASSWORD = $settings['USER_DB_PASSWORD']

$sql = if ($args.Count -gt 0) { $args[0] } else {
    "select username, role, status, email, is_2fa_enabled from users order by created_at"
}

& "$env:JAVA_HOME\bin\java.exe" -cp $driver (Join-Path $PSScriptRoot 'JdbcQuery.java') $sql
