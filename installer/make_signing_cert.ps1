<#
.SYNOPSIS
    Create (once) a self-signed code-signing certificate for Wellness Companion
    and, optionally, trust it on this machine.

.DESCRIPTION
    build_installer.bat calls this before signing "Wellness Companion.exe" and the
    generated Setup exe. The cert lives in the current user's personal store
    (Cert:\CurrentUser\My) and is found by signtool via its subject name ("/n"),
    so no .pfx file or password is ever stored in the repo. Idempotent: an
    existing matching cert is reused.

    A self-signed cert produces a VALID Authenticode signature but is NOT chained
    to a public CA, so Windows SmartScreen still warns "unknown publisher" on
    OTHER machines. Run with -Trust (as Administrator) to install it into this
    machine's Trusted Root + Trusted Publisher stores so the signature shows as
    fully trusted locally.

.PARAMETER Subject
    Certificate subject / publisher name. Must match the "/n" name used by
    build_installer.bat (default "CN=Wellness Companion Self-Signed").

.PARAMETER Trust
    Also install the cert into LocalMachine Root + TrustedPublisher (needs admin).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File make_signing_cert.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File make_signing_cert.ps1 -Trust
#>
[CmdletBinding()]
param(
    [string] $Subject = 'CN=Wellness Companion Self-Signed',
    [switch] $Trust
)

$ErrorActionPreference = 'Stop'

$existing = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert -ErrorAction SilentlyContinue |
    Where-Object { $_.Subject -eq $Subject } |
    Sort-Object NotAfter -Descending | Select-Object -First 1

if ($existing) {
    Write-Host "[OK] Code-signing cert already present: $($existing.Thumbprint) (expires $($existing.NotAfter.ToString('yyyy-MM-dd')))"
    $cert = $existing
} else {
    Write-Host "Creating self-signed code-signing cert: $Subject"
    $cert = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject $Subject `
        -KeyUsage DigitalSignature `
        -KeyAlgorithm RSA -KeyLength 2048 `
        -HashAlgorithm SHA256 `
        -CertStoreLocation Cert:\CurrentUser\My `
        -NotAfter (Get-Date).AddYears(5) `
        -FriendlyName 'Wellness Companion Self-Signed Code Signing'
    Write-Host "[OK] Created: $($cert.Thumbprint) (expires $($cert.NotAfter.ToString('yyyy-MM-dd')))"
}

if ($Trust) {
    Write-Host 'Installing cert into LocalMachine Root + TrustedPublisher (requires admin)...'
    foreach ($storeName in @('Root', 'TrustedPublisher')) {
        $store = New-Object System.Security.Cryptography.X509Certificates.X509Store($storeName, 'LocalMachine')
        $store.Open('ReadWrite')
        $store.Add($cert)
        $store.Close()
        Write-Host "[OK] Added to LocalMachine\$storeName"
    }
    Write-Host "[OK] The signature is now trusted on THIS machine. Other machines still see 'unknown publisher' (self-signed)."
}

# Emit the thumbprint last so callers can capture it if needed.
$cert.Thumbprint
