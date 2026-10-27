<#
.SYNOPSIS
    Builds the service images and loads them into the kind cluster.

.EXAMPLE
    ./k8s/build-images.ps1                                  # all services
    ./k8s/build-images.ps1 -Services booking-service        # just one
#>
param(
    [string[]] $Services = @(
        'service-registry', 'api-gateway', 'user-service', 'event-service',
        'inventory-service', 'payment-service', 'booking-service', 'notification-service'
    ),
    [string] $Cluster = 'event-booking',
    [string] $Tag = 'local'
)

# No $ErrorActionPreference = 'Stop': Windows PowerShell turns docker's progress output
# on stderr into terminating errors. Failures are detected via $LASTEXITCODE instead.
$root = Split-Path -Parent $PSScriptRoot

foreach ($service in $Services) {
    $image = "event-booking/${service}:$Tag"
    Write-Host "==> Building $image"
    docker build -t $image (Join-Path $root $service)
    if ($LASTEXITCODE -ne 0) { throw "docker build failed for $service" }

    Write-Host "==> Loading $image into kind cluster '$Cluster'"
    kind load docker-image $image --name $Cluster
    if ($LASTEXITCODE -ne 0) { throw "kind load failed for $service" }
}

Write-Host "Done. Restart running pods to pick up rebuilt images, for example:"
Write-Host "  kubectl rollout restart deployment/$($Services[0])"
