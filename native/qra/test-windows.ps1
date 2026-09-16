param([string]$BuildDirectory = "$PSScriptRoot/target/recursive")
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;
public static class QraTestErrorMode {
    [DllImport("kernel32.dll")] public static extern uint SetErrorMode(uint mode);
}
'@
# Child test processes inherit this. Loader failures become test failures,
# never modal dialogs on the user's desktop.
$previousMode = [QraTestErrorMode]::SetErrorMode(0x8003)
try {
    & ctest --test-dir $BuildDirectory -C Release --output-on-failure
    $testExit = $LASTEXITCODE
} finally {
    [QraTestErrorMode]::SetErrorMode($previousMode) | Out-Null
}
exit $testExit
