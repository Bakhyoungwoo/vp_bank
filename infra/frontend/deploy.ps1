param(
  [string]$FrontendPath = "C:\Users\5131\Desktop\vp\vp_front"
)

$ErrorActionPreference = "Stop"
$terraformPath = Join-Path $PSScriptRoot "..\terraform"
$bucket = terraform -chdir=$terraformPath output -raw frontend_bucket
$distribution = terraform -chdir=$terraformPath output -raw cloudfront_distribution_id

if (-not (Test-Path (Join-Path $FrontendPath "index.html"))) {
  throw "index.html was not found under $FrontendPath"
}

aws s3 sync $FrontendPath "s3://$bucket" --delete --exclude ".git/*" --exclude "legacy/*"
aws cloudfront create-invalidation --distribution-id $distribution --paths "/*"
Write-Output "Frontend deployed: $(terraform -chdir=$terraformPath output -raw cloudfront_domain)"
