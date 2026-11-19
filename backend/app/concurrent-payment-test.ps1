
$BaseUrl = "http://localhost:8081"

$SecureToken = Read-Host "Paste your JWT"
$Token = $SecureToken.Trim()

$PaymentId = "4c06d200-75c6-419c-a642-e4b18a145811"

$Headers = @{
    Authorization = "Bearer $Token"
    "Idempotency-Key" = "payflow-concurrent-refund-001"
}

$Body = @{
    amount = 10.00
} | ConvertTo-Json

$RequestCount = 10

$Jobs = 1..$RequestCount | ForEach-Object {
    Start-Job -ArgumentList $BaseUrl, $PaymentId, $Headers, $Body -ScriptBlock {
        param($Url, $Id, $RequestHeaders, $RequestBody)

        try {
            $Response = Invoke-RestMethod `
                -Uri "$Url/api/v1/payments/$Id/refunds" `
                -Method POST `
                -Headers $RequestHeaders `
                -ContentType "application/json" `
                -Body $RequestBody

            [PSCustomObject]@{
                Success = $true
                RefundId = $Response.id
                Status = $Response.status
                Error = $null
            }
        }
        catch {
            [PSCustomObject]@{
                Success = $false
                RefundId = $null
                Status = $null
                Error = $_.Exception.Message
            }
        }
    }
}

$Results = $Jobs | Wait-Job | Receive-Job
$Jobs | Remove-Job

Write-Host "`n--- CONCURRENT REFUND RESULTS ---" -ForegroundColor Cyan
$Results | Format-Table -AutoSize

$Successful = @($Results | Where-Object { $_.Success })
$UniqueIds = @($Successful | Select-Object -ExpandProperty RefundId -Unique)

Write-Host "Total requests: $RequestCount"
Write-Host "Successful requests: $($Successful.Count)"
Write-Host "Unique refund IDs: $($UniqueIds.Count)"

if ($Successful.Count -eq $RequestCount -and $UniqueIds.Count -eq 1) {
    Write-Host "`nPASS: All requests returned the same Refund ID." -ForegroundColor Green
}
else {
    Write-Host "`nINVESTIGATE: Responses differ or some requests failed." -ForegroundColor Yellow
}
