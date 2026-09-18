$ErrorActionPreference = "Stop"

$frontendDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$rootDir = Split-Path -Parent $frontendDir
$backendDir = Join-Path $rootDir "backend"
$backendRunner = Join-Path $backendDir "run-local.ps1"
$backendLog = Join-Path $backendDir "backend-dev.log"
$backendErrLog = Join-Path $backendDir "backend-dev.err.log"
$viteBin = Join-Path $frontendDir "node_modules\.bin\vite.cmd"

function Test-LocalPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$TimeoutMs = 1000
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connectTask = $client.ConnectAsync("localhost", $Port)
        if (-not $connectTask.Wait($TimeoutMs)) {
            return $false
        }

        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalPort -Port $Port) {
            return $true
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Ensure-Postgres {
    if (Test-LocalPort -Port 5432) {
        return
    }

    Write-Host "Postgres is not listening on localhost:5432. Trying docker compose up -d postgres-ai..."
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        Push-Location $rootDir
        try {
            & docker compose up -d postgres-ai
        } finally {
            Pop-Location
        }
    }

    if (-not (Wait-ForPort -Port 5432 -TimeoutSeconds 45)) {
        Write-Error "Postgres did not start on localhost:5432. Start Docker Desktop, then run: docker compose up -d postgres-ai"
        exit 1
    }
}

function Ensure-Backend {
    if (Test-LocalPort -Port 8080) {
        Write-Host "Backend is already running on localhost:8080."
        return
    }

    if (-not (Test-Path $backendRunner)) {
        Write-Error "Cannot find backend runner: $backendRunner"
        exit 1
    }

    Ensure-Postgres

    Write-Host "Starting backend on localhost:8080..."
    Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $backendRunner) `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrLog `
        -WindowStyle Hidden | Out-Null

    if (-not (Wait-ForPort -Port 8080 -TimeoutSeconds 90)) {
        Write-Error "Backend did not start on localhost:8080. Check $backendLog and $backendErrLog"
        exit 1
    }

    Write-Host "Backend is ready on localhost:8080."
}

Ensure-Backend

if (-not (Test-Path $viteBin)) {
    Write-Error "Cannot find Vite binary. Run npm install in $frontendDir first."
    exit 1
}

Write-Host "Starting Vite..."
& $viteBin --host localhost
