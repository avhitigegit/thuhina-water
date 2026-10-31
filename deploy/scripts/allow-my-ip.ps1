# Lets this PC open the UAT site (plan D-U5). Run it again whenever the internet connection changes
# (home router <-> mobile hotspot): it replaces the address in the "my-ip" firewall group with today's address.
#
#   aws login --profile thuhina-uat          (only when the login has expired)
#   .\deploy\scripts\allow-my-ip.ps1
#
# Port 80 is open to everyone but only redirects to HTTPS; port 443 (the app) is open only to the
# addresses in this group and in allowed_cidrs (Terraform).

param(
    [string]$AwsProfile = 'thuhina-uat',
    [string]$Region = 'ap-southeast-2',
    [string]$GroupName = 'thuhina-uat-my-ip'
)

$ErrorActionPreference = 'Stop'

function Invoke-Aws {
    $out = & aws @args --profile $AwsProfile --region $Region --output text
    if ($LASTEXITCODE -ne 0) { throw "aws $($args -join ' ') failed - is the login still valid? Run: aws login --profile $AwsProfile" }
    return $out
}

$ip = (Invoke-RestMethod -Uri 'https://checkip.amazonaws.com' -TimeoutSec 10).Trim()
if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw "Could not read this PC's public address (got '$ip')." }
Write-Host "This PC's public address: $ip"

$groupId = Invoke-Aws ec2 describe-security-groups --filters "Name=group-name,Values=$GroupName" --query 'SecurityGroups[0].GroupId'
if (-not $groupId -or $groupId -eq 'None') { throw "Security group $GroupName not found." }

# Remove the old addresses, then add today's.
$ruleIds = Invoke-Aws ec2 describe-security-group-rules --filters "Name=group-id,Values=$groupId" `
    --query 'SecurityGroupRules[?!IsEgress].SecurityGroupRuleId'
$ruleIds = @("$ruleIds" -split '\s+' | Where-Object { $_ })
if ($ruleIds.Count -gt 0) {
    Invoke-Aws ec2 revoke-security-group-ingress --group-id $groupId --security-group-rule-ids @ruleIds | Out-Null
    Write-Host "Removed $($ruleIds.Count) old address(es)."
}

Invoke-Aws ec2 authorize-security-group-ingress --group-id $groupId --protocol tcp --port 443 --cidr "$ip/32" | Out-Null
Write-Host "Done - this PC can open the UAT site now." -ForegroundColor Green
