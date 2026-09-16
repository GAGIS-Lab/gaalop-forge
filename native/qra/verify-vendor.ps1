param([string]$VendorDirectory = "$PSScriptRoot/vendor")
$ErrorActionPreference = 'Stop'
$vendorRoot = (Resolve-Path -LiteralPath $VendorDirectory).Path
$entries = Get-Content -LiteralPath (Join-Path $vendorRoot 'SHA256.json') -Raw | ConvertFrom-Json
foreach ($entry in $entries) {
    $sourcePath = Join-Path $vendorRoot $entry.path
    $actualHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    if ($actualHash -ne $entry.sha256) {
        throw "Vendor source mismatch: $($entry.path)"
    }
}
Write-Output "Verified $($entries.Count) imported source/license files."
