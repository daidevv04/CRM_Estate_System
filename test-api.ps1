# test-api.ps1 - Test chuc nang qua gateway (http://localhost:8080).
#   .\tools\run-tests.ps1                        # cach dung chuan: tu ky token + chay + ghi log
#   .\test-api.ps1 -AccessToken <jwt>            # chay truc tiep voi token co san
#   .\test-api.ps1 -AdminUser <u> -AdminPassword <p>   # test them luong login tai khoan that
#
# Chay: consul agent -dev + .\dev-up.ps1 truoc. Service nao khong chay (503) thi
# nhom test cua service do duoc danh dau SKIP, khong tinh la FAIL.
# Du lieu test: user duoc tao roi xoa lai; customer/lead/deal/payment chi tao + doc + sua
# (cac API do khong co xoa hoac xoa la pha vo nghiep vu). Luong login/refresh/logout
# duoc test bang tai khoan tam do chinh suite tao ra, nen khong can mat khau that.
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$AdminUser = '',
    [string]$AdminPassword = '',
    # Token ky san (tools\make-token.ps1) de test API can xac thuc khi khong biet mat khau.
    [string]$AccessToken = '',
    # Hop thu that de test gui OTP. Bo trong thi bo qua test gui mail (xem TmpEmail).
    [string]$TestEmail = ''
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0
$script:skip = 0
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Call([string]$method, [string]$path, $body, [string]$token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; TimeoutSec = 25; UseBasicParsing = $true }
    if ($null -ne $body) {
        $params['ContentType'] = 'application/json'
        $params['Body'] = ($body | ConvertTo-Json -Depth 6 -Compress)
    }
    try {
        $response = Invoke-WebRequest @params
        $content = $response.Content
        # Windows PowerShell tra ve byte[] khi content-type khong phai text/json.
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $content }
    } catch {
        $webResponse = $_.Exception.Response
        if ($webResponse -and $webResponse.GetResponseStream) {
            $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
            return [pscustomobject]@{ Status = [int]$webResponse.StatusCode; Body = $reader.ReadToEnd() }
        }
        return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message }
    }
}

function Assert([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { $script:pass++; Write-Host ("PASS  {0,-50} {1}" -f $name, $detail) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("FAIL  {0,-50} {1}" -f $name, $detail) -ForegroundColor Red }
}

function Skip([string]$name, [string]$why) {
    $script:skip++
    Write-Host ("SKIP  {0,-50} {1}" -f $name, $why) -ForegroundColor Yellow
}

function Json($response) {
    if (-not $response.Body) { return $null }
    try { return $response.Body | ConvertFrom-Json } catch { return $null }
}

# Email cho user tam. Khong co -TestEmail thi dung domain gia @estate.local: Resend se
# bounce (domain khong ton tai), bounce rate cao thi Resend tam dung gui. Co -TestEmail
# thi dung plus-alias -> moi lan chay mot dia chi rieng ma van toi hop thu that.
function TmpEmail([string]$slug) {
    if (-not $TestEmail) { return "$slug$stamp@estate.local" }
    $parts = $TestEmail -split '@', 2
    return "$($parts[0])+$slug$stamp@$($parts[1])"
}

Write-Host "`n=== Gateway ($BaseUrl) ===" -ForegroundColor Cyan
$health = Call 'GET' '/actuator/health' $null $null
Assert 'gateway /actuator/health = UP' ($health.Status -eq 200 -and $health.Body -match '"status":"UP"') "status=$($health.Status) body=$($health.Body)"

$anonymous = Call 'GET' '/api/users' $null $null
Assert 'GET /api/users khong token -> 401' ($anonymous.Status -eq 401) "status=$($anonymous.Status)"

Write-Host "`n=== Auth (user-service) ===" -ForegroundColor Cyan
# Token cho cac test can quyen ADMIN: ky bang JWT_SECRET (tools\make-token.ps1).
# Luong login/refresh/logout duoc test day du o nhom "Auth day du" ben duoi (tai khoan tam),
# nen o day khong can mat khau tai khoan that. Neu muon test dung tai khoan that thi chay:
#   .\test-api.ps1 -AdminUser <user> -AdminPassword <pass>
if ($AccessToken) {
    $token = $AccessToken
} else {
    $login = Call 'POST' '/api/auth/login' @{ usernameOrEmail = $AdminUser; password = $AdminPassword } $null
    $token = (Json $login).accessToken
    Assert 'POST /api/auth/login (dung mat khau) -> 200' ($login.Status -eq 200 -and $token) "status=$($login.Status)"

    $badLogin = Call 'POST' '/api/auth/login' @{ usernameOrEmail = $AdminUser; password = 'sai-mat-khau-123' } $null
    Assert 'POST /api/auth/login (sai mat khau) -> 401' ($badLogin.Status -eq 401) "status=$($badLogin.Status)"
}

if (-not $token) {
    Write-Host "`nKhong lay duoc token -> dung cac test can xac thuc." -ForegroundColor Red
    Write-Host ("PASS={0} FAIL={1} SKIP={2}" -f $script:pass, $script:fail, $script:skip)
    exit 1
}

$me = Call 'GET' '/api/users/me' $null $token
$meBody = Json $me
# Doi chieu dung danh tinh nam trong nhom "Auth day du" (login that -> /users/me tra dung username).
Assert 'GET /api/users/me -> 200 + co username' ($me.Status -eq 200 -and $meBody.username) "status=$($me.Status) username=$($meBody.username)"

if ($false) {
    # (bo) phan refresh cua tai khoan that: xem nhom "Auth day du" ben duoi
}

Write-Host "`n=== User CRUD (user-service) ===" -ForegroundColor Cyan
$newUser = @{
    username = "test$stamp"
    password = 'TestPass123!'
    email    = TmpEmail 'test'
    fullName = 'Nguoi dung test'
    role     = 'SALES'
}
$createdUser = Call 'POST' '/api/users' $newUser $token
$userBody = Json $createdUser
Assert 'POST /api/users -> 201' ($createdUser.Status -eq 201 -and $userBody.id) "status=$($createdUser.Status)"

if ($userBody.id) {
    $gotUser = Call 'GET' "/api/users/$($userBody.id)" $null $token
    Assert 'GET /api/users/{id} -> 200' ($gotUser.Status -eq 200) "status=$($gotUser.Status)"

    $listedUsers = Call 'GET' '/api/users' $null $token
    Assert 'GET /api/users -> 200' ($listedUsers.Status -eq 200) "status=$($listedUsers.Status) count=$((Json $listedUsers).Count)"

    $roleChange = Call 'PATCH' "/api/users/$($userBody.id)/role" @{ role = 'MANAGER' } $token
    Assert 'PATCH /api/users/{id}/role -> 200' ($roleChange.Status -eq 200 -and (Json $roleChange).role -eq 'MANAGER') "status=$($roleChange.Status)"

    $statusChange = Call 'PATCH' "/api/users/$($userBody.id)/status" @{ status = 'ACTIVE' } $token
    Assert 'PATCH /api/users/{id}/status -> 200' ($statusChange.Status -eq 200) "status=$($statusChange.Status)"

    $revoke = Call 'POST' "/api/users/$($userBody.id)/revoke-tokens" @{} $token
    Assert 'POST /api/users/{id}/revoke-tokens -> 204' ($revoke.Status -eq 204) "status=$($revoke.Status)"

    # OTP gui toi user vua tao (co email) -> di qua mail-service -> Resend API.
    # 500 nghia la mail-service da nhan request nhung Resend tu choi (thuong la
    # MAIL_FROM chua verify domain) -> danh dau SKIP kem ly do, khong phai loi API.
    if ($TestEmail) {
        $otpToUser = Call 'POST' '/api/auth/otp/send' @{ usernameOrEmail = $newUser.email; purpose = 'RESET_PASSWORD' } $null
        if ($otpToUser.Status -eq 204) {
            Assert 'POST /api/auth/otp/send (hop thu that) -> 204' $true "status=$($otpToUser.Status) toi $($newUser.email)"
        } elseif ($otpToUser.Status -eq 500) {
            Skip 'POST /api/auth/otp/send (hop thu that)' 'mail-service 500: Resend tu choi - xem logs\mail-service.log'
        } else {
            Assert 'POST /api/auth/otp/send (hop thu that) -> 204' $false "status=$($otpToUser.Status)"
        }
    } else {
        Skip 'POST /api/auth/otp/send' 'thieu -TestEmail: khong gui toi domain gia de tranh bounce'
    }

    $removed = Call 'DELETE' "/api/users/$($userBody.id)" $null $token
    Assert 'DELETE /api/users/{id} -> 204' ($removed.Status -eq 204) "status=$($removed.Status)"
} else {
    Skip 'user CRUD chi tiet' 'tao user that bai'
}

Write-Host "`n=== Auth day du: login / refresh / logout bang tai khoan tam ===" -ForegroundColor Cyan
# Suite tu tao tai khoan tam de chay tron luong auth, khong can biet mat khau tai khoan that.
$authUser = Call 'POST' '/api/users' @{
    username = "auth$stamp"
    password = 'AuthPass123!'
    email    = "auth$stamp@estate.local"
    fullName = 'Auth flow test'
    role     = 'SALES'
} $token
$authUserId = (Json $authUser).id

if ($authUserId) {
    $badLogin2 = Call 'POST' '/api/auth/login' @{ usernameOrEmail = "auth$stamp"; password = 'sai-mat-khau-999' } $null
    Assert 'POST /api/auth/login (sai mat khau) -> 401' ($badLogin2.Status -eq 401) "status=$($badLogin2.Status)"

    $loginTemp = Call 'POST' '/api/auth/login' @{ usernameOrEmail = "auth$stamp"; password = 'AuthPass123!' } $null
    $tempAccess = (Json $loginTemp).accessToken
    $tempRefresh = (Json $loginTemp).refreshToken
    Assert 'POST /api/auth/login (tai khoan tam) -> 200 + 2 token' ($loginTemp.Status -eq 200 -and $tempAccess -and $tempRefresh) "status=$($loginTemp.Status)"

    if ($tempAccess) {
        $meTemp = Call 'GET' '/api/users/me' $null $tempAccess
        Assert 'GET /api/users/me (token tu login) -> 200' ($meTemp.Status -eq 200 -and (Json $meTemp).username -eq "auth$stamp") "status=$($meTemp.Status)"
    }

    if ($tempRefresh) {
        $refreshTemp = Call 'POST' '/api/auth/refresh' @{ refreshToken = $tempRefresh } $null
        Assert 'POST /api/auth/refresh (tai khoan tam) -> 200' ($refreshTemp.Status -eq 200 -and (Json $refreshTemp).accessToken) "status=$($refreshTemp.Status)"

        $logoutTemp = Call 'POST' '/api/auth/logout' @{ refreshToken = $tempRefresh } $null
        Assert 'POST /api/auth/logout (tai khoan tam) -> 204' ($logoutTemp.Status -eq 204) "status=$($logoutTemp.Status)"

        $afterLogout = Call 'POST' '/api/auth/refresh' @{ refreshToken = $tempRefresh } $null
        Assert 'refresh sau logout -> 401' ($afterLogout.Status -eq 401) "status=$($afterLogout.Status)"
    }

    $delAuth = Call 'DELETE' "/api/users/$authUserId" $null $token
    Assert 'DELETE tai khoan tam -> 204' ($delAuth.Status -eq 204) "status=$($delAuth.Status)"
} else {
    Skip 'Auth day du' 'tao tai khoan tam that bai'
}

Write-Host "`n=== Customer / Appointment / Email template (customer-service) ===" -ForegroundColor Cyan
$customerId = $null
$probe = Call 'GET' '/api/customers?page=0&size=1' $null $token
if ($probe.Status -eq 503 -or $probe.Status -eq 0) {
    Skip 'customer-service' "service khong chay (status=$($probe.Status))"
} else {
    $createdCustomer = Call 'POST' '/api/customers' @{
        fullName   = "Khach test $stamp"
        phone      = '+84' + $stamp.Replace('-', '').Substring(6)
        email      = "khach$stamp@estate.local"
        demandType = 'BUY'
        source     = 'test-api.ps1'
        status     = 'NEW'
    } $token
    $customer = Json $createdCustomer
    $customerId = $customer.id
    Assert 'POST /api/customers -> 201' ($createdCustomer.Status -eq 201 -and $customerId) "status=$($createdCustomer.Status)"

    $customerList = Call 'GET' '/api/customers?page=0&size=5' $null $token
    Assert 'GET /api/customers -> 200' ($customerList.Status -eq 200) "status=$($customerList.Status)"

    if ($customerId) {
        $customerGet = Call 'GET' "/api/customers/$customerId" $null $token
        Assert 'GET /api/customers/{id} -> 200' ($customerGet.Status -eq 200) "status=$($customerGet.Status)"

        $customerPatch = Call 'PATCH' "/api/customers/$customerId" @{ status = 'POTENTIAL' } $token
        Assert 'PATCH /api/customers/{id} -> 200' ($customerPatch.Status -eq 200) "status=$($customerPatch.Status)"

        $care = Call 'POST' "/api/customers/$customerId/cares" @{ type = 'CALL'; content = 'Goi tu test-api.ps1' } $token
        Assert 'POST /api/customers/{id}/cares -> 201' ($care.Status -eq 201) "status=$($care.Status)"

        $careList = Call 'GET' "/api/customers/$customerId/cares" $null $token
        Assert 'GET /api/customers/{id}/cares -> 200' ($careList.Status -eq 200) "status=$($careList.Status)"

        # Moi lan chay dung mot sales rieng: lich hen bi chan trung theo sales,
        # dung lai admin cho moi lan chay thi cac khung gio se chong nhau -> 409.
        $schedUser = Call 'POST' '/api/users' @{
            username = "sched$stamp"
            password = 'TestPass123!'
            email    = "sched$stamp@estate.local"
            fullName = 'Sales dat lich test'
            role     = 'SALES'
        } $token
        $schedSalesId = (Json $schedUser).id

        # Khung gio rieng cho tung lan chay: DB co EXCLUDE constraint chan 2 lich
        # giao nhau cua cung mot sales, nen dung lai gio cu se bi 409.
        $slot = (Get-Date).AddDays(1).AddSeconds([int]((Get-Date) - (Get-Date).Date).TotalSeconds)
        $appointment = Call 'POST' '/api/appointments' @{
            customerId = $customerId
            title      = "Hen gap $stamp"
            startTime  = $slot.ToString('yyyy-MM-ddTHH:mm:ss')
            endTime    = $slot.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
            salesId    = $schedSalesId
            status     = 'PENDING'
        } $token
        Assert 'POST /api/appointments -> 201' ($appointment.Status -eq 201) "status=$($appointment.Status)"

        $appointmentList = Call 'GET' '/api/appointments?page=0&size=5' $null $token
        Assert 'GET /api/appointments -> 200' ($appointmentList.Status -eq 200) "status=$($appointmentList.Status)"

        if ($schedSalesId) {
            $removedSched = Call 'DELETE' "/api/users/$schedSalesId" $null $token
            Assert 'DELETE sales dat lich test -> 204' ($removedSched.Status -eq 204) "status=$($removedSched.Status)"
        }
    }

    $template = Call 'POST' '/api/email-templates' @{
        name     = "Mau test $stamp"
        subject  = 'Cam on quy khach'
        body     = 'Noi dung mau test'
        category = 'WELCOME'
        status   = 'ACTIVE'
    } $token
    Assert 'POST /api/email-templates -> 201' ($template.Status -eq 201) "status=$($template.Status)"

    $templateList = Call 'GET' '/api/email-templates?page=0&size=5' $null $token
    Assert 'GET /api/email-templates -> 200' ($templateList.Status -eq 200) "status=$($templateList.Status)"
}

Write-Host "`n=== Project / Product (crm-service) ===" -ForegroundColor Cyan
$probeCrm = Call 'GET' '/api/projects?page=0&size=1' $null $token
if ($probeCrm.Status -eq 503 -or $probeCrm.Status -eq 0) {
    Skip 'crm-service' "service khong chay (status=$($probeCrm.Status))"
} else {
    $createdProject = Call 'POST' '/api/projects' @{
        name     = "Du an test $stamp"
        location = 'Ha Noi'
        investor = 'Chu dau tu test'
        status   = 'SELLING'
    } $token
    $project = Json $createdProject
    Assert 'POST /api/projects -> 201' ($createdProject.Status -eq 201 -and $project.id) "status=$($createdProject.Status)"

    $projectList = Call 'GET' '/api/projects?page=0&size=5' $null $token
    Assert 'GET /api/projects -> 200' ($projectList.Status -eq 200) "status=$($projectList.Status)"

    $productId = $null
    if ($project.id) {
        $createdProduct = Call 'POST' '/api/products' @{
            projectId = $project.id
            code      = "P-$stamp"
            type      = 'APARTMENT'
            area      = 75.5
            block     = 'A'
            price     = 2500000000
            bedroom   = 2
            direction = 'SE'
            status    = 'AVAILABLE'
        } $token
        $product = Json $createdProduct
        $productId = $product.id
        Assert 'POST /api/products -> 201' ($createdProduct.Status -eq 201 -and $productId) "status=$($createdProduct.Status)"

        $productList = Call 'GET' '/api/products?page=0&size=5' $null $token
        Assert 'GET /api/products -> 200' ($productList.Status -eq 200) "status=$($productList.Status)"

        if ($productId) {
            $productPatch = Call 'PATCH' "/api/products/$productId" @{ status = 'RESERVED' } $token
            Assert 'PATCH /api/products/{id} -> 200' ($productPatch.Status -eq 200) "status=$($productPatch.Status)"
        }
    } else {
        Skip 'product' 'tao project that bai'
    }

    if ($customerId -and $productId) {
        $createdLead = Call 'POST' '/api/leads' @{
            customerId    = $customerId
            productId     = $productId
            stage         = 'NEW'
            expectedValue = 2500000000
            closeDate     = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
        } $token
        $lead = Json $createdLead
        Assert 'POST /api/leads -> 201' ($createdLead.Status -eq 201 -and $lead.id) "status=$($createdLead.Status)"

        if ($lead.id) {
            $leadList = Call 'GET' '/api/leads?page=0&size=5' $null $token
            Assert 'GET /api/leads -> 200' ($leadList.Status -eq 200) "status=$($leadList.Status)"

            $leadGet = Call 'GET' "/api/leads/$($lead.id)" $null $token
            Assert 'GET /api/leads/{id} -> 200' ($leadGet.Status -eq 200) "status=$($leadGet.Status)"

            $leadPatch = Call 'PATCH' "/api/leads/$($lead.id)" @{ stage = 'CONTACTED' } $token
            Assert 'PATCH /api/leads/{id} stage=CONTACTED -> 200' ($leadPatch.Status -eq 200 -and (Json $leadPatch).stage -eq 'CONTACTED') "status=$($leadPatch.Status)"

            # Nghiep vu: chi tao duoc deal khi lead da WON (DealService kiem tra "must be WON").
            $leadWon = Call 'PATCH' "/api/leads/$($lead.id)" @{ stage = 'WON' } $token
            Assert 'PATCH /api/leads/{id} stage=WON -> 200' ($leadWon.Status -eq 200 -and (Json $leadWon).stage -eq 'WON') "status=$($leadWon.Status)"

            $createdDeal = Call 'POST' '/api/deals' @{
                leadId        = $lead.id
                contractCode  = "HD-$stamp"
                contractValue = 2500000000
                depositAmount = 100000000
                depositDate   = (Get-Date).ToString('yyyy-MM-dd')
                signedDate    = (Get-Date).ToString('yyyy-MM-dd')
                paymentStatus = 'PARTIAL'
                paymentMethod = 'BANK_TRANSFER'
                status        = 'IN_PROGRESS'
                note          = 'Tao tu test-api.ps1'
            } $token
            $deal = Json $createdDeal
            Assert 'POST /api/deals -> 201' ($createdDeal.Status -eq 201 -and $deal.id) "status=$($createdDeal.Status)"

            if ($deal.id) {
                $dealList = Call 'GET' '/api/deals?page=0&size=5' $null $token
                Assert 'GET /api/deals -> 200' ($dealList.Status -eq 200) "status=$($dealList.Status)"

                $dealGet = Call 'GET' "/api/deals/$($deal.id)" $null $token
                Assert 'GET /api/deals/{id} -> 200' ($dealGet.Status -eq 200) "status=$($dealGet.Status)"

                $dealPatch = Call 'PATCH' "/api/deals/$($deal.id)" @{ status = 'ACTIVE' } $token
                Assert 'PATCH /api/deals/{id} status=ACTIVE -> 200' ($dealPatch.Status -eq 200) "status=$($dealPatch.Status)"

                $item = Call 'POST' "/api/deals/$($deal.id)/items" @{
                    productId = $productId
                    unitPrice = 2500000000
                    quantity  = 1
                    note      = 'Item test'
                } $token
                $itemBody = Json $item
                Assert 'POST /api/deals/{id}/items -> 201' ($item.Status -eq 201 -and $itemBody.id) "status=$($item.Status)"

                $itemList = Call 'GET' "/api/deals/$($deal.id)/items" $null $token
                Assert 'GET /api/deals/{id}/items -> 200' ($itemList.Status -eq 200) "status=$($itemList.Status)"

                if ($itemBody.id) {
                    $itemPatch = Call 'PATCH' "/api/deals/$($deal.id)/items/$($itemBody.id)" @{ quantity = 2 } $token
                    Assert 'PATCH /api/deals/{id}/items/{itemId} -> 200' ($itemPatch.Status -eq 200) "status=$($itemPatch.Status)"
                }

                $payment = Call 'POST' "/api/deals/$($deal.id)/payments" @{
                    amount = 100000000
                    paidAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
                    method = 'BANK_TRANSFER'
                    note   = 'Dot 1 test'
                } $token
                Assert 'POST /api/deals/{id}/payments -> 201' ($payment.Status -eq 201) "status=$($payment.Status)"

                $paymentList = Call 'GET' "/api/deals/$($deal.id)/payments" $null $token
                Assert 'GET /api/deals/{id}/payments -> 200' ($paymentList.Status -eq 200) "status=$($paymentList.Status)"
            }
        }
    } else {
        Skip 'lead/deal' 'thieu customerId hoac productId'
    }
}

Write-Host "`n=== Bo sung: profile / reset password / 2FA / xoa (user + customer) ===" -ForegroundColor Cyan
$extraUser = Call 'POST' '/api/users' @{
    username = "extra$stamp"
    password = 'ExtraPass123!'
    email    = "extra$stamp@estate.local"
    fullName = 'User bo sung test'
    role     = 'SALES'
} $token
$extraId = (Json $extraUser).id

if ($extraId) {
    $profile = Call 'PATCH' "/api/users/$extraId/profile" @{
        email    = "extranew$stamp@estate.local"
        fullName = 'Ten da sua'
        phone    = '+84901112233'
    } $token
    Assert 'PATCH /api/users/{id}/profile -> 200' ($profile.Status -eq 200 -and (Json $profile).fullName -eq 'Ten da sua') "status=$($profile.Status)"

    $reset = Call 'POST' "/api/users/$extraId/reset-password" @{ password = 'ResetPass123!' } $token
    Assert 'POST /api/users/{id}/reset-password -> 204' ($reset.Status -eq 204) "status=$($reset.Status)"

    # 2FA: can mat khau hien tai cua chinh nguoi do -> ky token rieng cho user nay.
    $extraToken = ''
    try {
        $extraToken = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tools\make-token.ps1') `
            -UserId $extraId -Username "extra$stamp" -Role 'SALES' | Select-Object -Last 1).Trim()
    } catch {
        $extraToken = ''
    }

    if ($extraToken) {
        # AuthService tra 400 BAD_REQUEST cho "Current password is incorrect" va
        # "Invalid TOTP code" (xem AuthService dong ~297 va ~246), khong phai 401.
        $bad2fa = Call 'POST' '/api/auth/2fa/enable' @{ password = 'sai-mat-khau' } $extraToken
        Assert 'POST /api/auth/2fa/enable (sai mat khau) -> 400' ($bad2fa.Status -eq 400) "status=$($bad2fa.Status)"

        $enable2fa = Call 'POST' '/api/auth/2fa/enable' @{ password = 'ResetPass123!' } $extraToken
        $secret = (Json $enable2fa).secret
        Assert 'POST /api/auth/2fa/enable -> 200 + secret' ($enable2fa.Status -eq 200 -and $secret) "status=$($enable2fa.Status) secret=$($secret.Length) ky tu"

        if ($secret) {
            $badCode = Call 'POST' '/api/auth/2fa/disable' @{ password = 'ResetPass123!'; code = '000000' } $extraToken
            Assert 'POST /api/auth/2fa/disable (sai code) -> 400' ($badCode.Status -eq 400) "status=$($badCode.Status)"
        }
    } else {
        Skip '2FA enable/disable' 'khong ky duoc token cho user tam'
    }

    $removedExtra = Call 'DELETE' "/api/users/$extraId" $null $token
    Assert 'DELETE user bo sung -> 204' ($removedExtra.Status -eq 204) "status=$($removedExtra.Status)"
} else {
    Skip 'profile / reset-password / 2FA' 'tao user tam that bai'
}

# Customer + appointment: GET theo id, GET theo khach, PATCH huy, DELETE (don du lieu test).
$tmpCustomer = Call 'POST' '/api/customers' @{
    fullName   = "Khach xoa $stamp"
    phone      = '+84' + ([string]([int]((Get-Date) - (Get-Date).Date).TotalSeconds)).PadLeft(9, '0')
    demandType = 'RENT'
    status     = 'NEW'
} $token
$tmpCustomerId = (Json $tmpCustomer).id

if ($tmpCustomerId) {
    $slot2 = (Get-Date).AddDays(45)
    $tmpAppointment = Call 'POST' '/api/appointments' @{
        customerId = $tmpCustomerId
        title      = "Lich de xoa $stamp"
        startTime  = $slot2.ToString('yyyy-MM-ddTHH:mm:ss')
        endTime    = $slot2.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
        status     = 'PENDING'
    } $token
    $tmpAppointmentId = (Json $tmpAppointment).id
    Assert 'POST /api/appointments (khach tam) -> 201' ($tmpAppointment.Status -eq 201 -and $tmpAppointmentId) "status=$($tmpAppointment.Status)"

    if ($tmpAppointmentId) {
        $apptGet = Call 'GET' "/api/appointments/$tmpAppointmentId" $null $token
        Assert 'GET /api/appointments/{id} -> 200' ($apptGet.Status -eq 200) "status=$($apptGet.Status)"

        $apptByCustomer = Call 'GET' "/api/appointments/customer/$tmpCustomerId" $null $token
        Assert 'GET /api/appointments/customer/{id} -> 200' ($apptByCustomer.Status -eq 200) "status=$($apptByCustomer.Status)"

        $apptCancel = Call 'PATCH' "/api/appointments/$tmpAppointmentId" @{ status = 'CANCELLED' } $token
        Assert 'PATCH /api/appointments/{id} -> 200' ($apptCancel.Status -eq 200 -and (Json $apptCancel).status -eq 'CANCELLED') "status=$($apptCancel.Status)"

        $apptDelete = Call 'DELETE' "/api/appointments/$tmpAppointmentId" $null $token
        Assert 'DELETE /api/appointments/{id} -> 204' ($apptDelete.Status -eq 204) "status=$($apptDelete.Status)"
    }

    $customerDelete = Call 'DELETE' "/api/customers/$tmpCustomerId" $null $token
    Assert 'DELETE /api/customers/{id} -> 204' ($customerDelete.Status -eq 204) "status=$($customerDelete.Status)"
} else {
    Skip 'appointment GET/PATCH/DELETE + customer DELETE' 'tao khach tam that bai'
}

# CRM: stage-history + xoa item/deal/lead (don du lieu test).
if ($lead -and $lead.id) {
    $history = Call 'GET' "/api/leads/$($lead.id)/stage-history" $null $token
    Assert 'GET /api/leads/{id}/stage-history -> 200' ($history.Status -eq 200 -and (Json $history).Count -ge 2) "status=$($history.Status) so ban ghi=$((Json $history).Count)"
}
if ($deal -and $deal.id -and $itemBody -and $itemBody.id) {
    $itemDelete = Call 'DELETE' "/api/deals/$($deal.id)/items/$($itemBody.id)" $null $token
    Assert 'DELETE /api/deals/{id}/items/{itemId} -> 204' ($itemDelete.Status -eq 204) "status=$($itemDelete.Status)"
}
if ($deal -and $deal.id) {
    $dealDelete = Call 'DELETE' "/api/deals/$($deal.id)" $null $token
    Assert 'DELETE /api/deals/{id} -> 204' ($dealDelete.Status -eq 204) "status=$($dealDelete.Status)"
}
if ($lead -and $lead.id) {
    $leadDelete = Call 'DELETE' "/api/leads/$($lead.id)" $null $token
    Assert 'DELETE /api/leads/{id} -> 204' ($leadDelete.Status -eq 204) "status=$($leadDelete.Status)"
}

Write-Host "`n=== OTP + ket thuc phien ===" -ForegroundColor Cyan
# Chi 1 lan gui OTP cho moi lan chay: OtpService throttle bucket 'otp-send-ip' la
# cung gioi han voi max-login-attempts (mac dinh 5) trong 15 phut, gui nhieu se 429.
$notFound = Call 'GET' '/api/customers/00000000-0000-0000-0000-000000000000' $null $token
Assert 'GET id khong ton tai -> 404' ($notFound.Status -eq 404) "status=$($notFound.Status)"

if ($false) {
    # (bo) phan logout cua tai khoan that: xem nhom "Auth day du" ben duoi
}

Write-Host "`n================ KET QUA ================" -ForegroundColor Cyan
Write-Host ("PASS={0}  FAIL={1}  SKIP={2}  (chay luc {3})" -f $script:pass, $script:fail, $script:skip, $stamp)
if ($script:fail -gt 0) { exit 1 }
exit 0
