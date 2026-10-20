# Lanza la prueba de CLIENTE del Config Menu (Minecraft real con ventana, capturas) SOLO si es seguro:
#  - no hay ningun Minecraft abierto (javaw.exe): no se pisa la partida de Gravis;
#  - hay RAM disponible de sobra (Gradle ~1 GB + el cliente modeado con Create Fly ~2-3 GB).
# Uso:
#   .\herramientas\probar-cliente.ps1                 lanza la prueba (ventana de Minecraft ~2-4 min, se cierra sola)
#   .\herramientas\probar-cliente.ps1 -SoloComprobar  solo mira si seria seguro lanzar; no lanza nada
#   .\herramientas\probar-cliente.ps1 -SoloInforme    no lanza: resume la ultima ejecucion (marcadores [CFCM-TEST], capturas)
#   .\herramientas\probar-cliente.ps1 -ConAddons      carga tambien Dragons Plus p3 y Connected (jars locales, rutas abajo; -Connected '' lo salta)
#   OJO 2026-10-19: Connected 1.3.3-mc26.2-3/-4 NO arranca con Create Fly 26.2-6.0.9-2 (falta EncasedCogVisual): usar -Connected ''
# La tarea de Gradle acaba en FAILED aunque todo vaya bien (vigilante de apagado de Flywheel): manda la linea "[CFCM-TEST] ALL OK".
param(
    [string]$Proyecto = (Join-Path $PSScriptRoot '..'),
    [int]$MinimoMB = 3000,
    [string]$Log = (Join-Path $env:TEMP 'cfcm-client-gametest.log'),
    [switch]$SoloComprobar,
    [switch]$SoloInforme,
    [switch]$ConAddons,
    [string]$DragonsPlus = 'E:\DEV\MC MODS\PORTS\CENTRAL KITCHEN\local-maven\plus\dragons\createdragonsplus\create-dragons-plus-fly-26.2-rc-2\1.11.4-p3\create-dragons-plus-fly-26.2-rc-2-1.11.4-p3.jar',
    [string]$Connected = 'E:\DEV\create connected\build\libs\create_connected-fly-1.3.3-mc26.2-4.jar'
)
$ErrorActionPreference = 'Stop'
$Proyecto = (Resolve-Path $Proyecto).Path
$ejecucion = Join-Path $Proyecto 'build\run\clientGameTest'

function Resumen {
    Write-Host ''
    Write-Host '=== Marcadores de la prueba ==='
    if (Test-Path $Log) {
        $marcas = Select-String -Path $Log -Pattern '\[CFCM-TEST\]' | ForEach-Object { $_.Line }
        if ($marcas) { $marcas | ForEach-Object { Write-Host $_ } } else { Write-Host '(ningun marcador: la prueba no llego a arrancar)' }
    } else { Write-Host "(no existe el log $Log)" }
    Write-Host ''
    Write-Host '=== Capturas ==='
    $capturas = Get-ChildItem (Join-Path $ejecucion 'screenshots') -Filter *.png -ErrorAction SilentlyContinue | Sort-Object Name
    if ($capturas) { $capturas | ForEach-Object { Write-Host ('{0,-70} {1,6} KB' -f $_.FullName, [int]($_.Length / 1KB)) } } else { Write-Host '(sin capturas)' }
    Write-Host ''
    Write-Host '=== Avisos del log del juego ==='
    $juego = Join-Path $ejecucion 'logs\latest.log'
    if (Test-Path $juego) {
        $avisos = Select-String -Path $juego -Pattern 'Exception|Mixin apply failed|InjectionError|Minecraft has crashed|flyconfigmenu|gravis_flying_config_menu' |
            Where-Object { $_.Line -notmatch 'NoSuchFileException' } | Select-Object -First 25 | ForEach-Object { $_.Line }
        if ($avisos) { $avisos | ForEach-Object { Write-Host $_ } } else { Write-Host '(ninguno)' }
    } else { Write-Host '(sin latest.log)' }
}

if ($SoloInforme) { Resumen; return }

$abiertos = Get-Process javaw -ErrorAction SilentlyContinue | ForEach-Object { "PID $($_.Id)" }
$libreMB = [int](Get-Counter '\Memory\Available MBytes').CounterSamples[0].CookedValue
Write-Host "RAM disponible: $libreMB MB (minimo $MinimoMB MB). Minecraft abierto: $(if ($abiertos) { $abiertos -join '; ' } else { 'no' })"
if ($abiertos) { Write-Host 'NO LANZO: hay un Minecraft abierto (puede ser la partida de Gravis).'; return }
if ($libreMB -lt $MinimoMB) { Write-Host "NO LANZO: poca RAM disponible ($libreMB MB < $MinimoMB MB). Cierra programas pesados y repite."; return }
if ($SoloComprobar) { Write-Host 'Seria seguro lanzar.'; return }

Remove-Item (Join-Path $ejecucion 'screenshots') -Recurse -Force -ErrorAction SilentlyContinue
Set-Location $Proyecto
Write-Host 'Lanzando runClientGameTest (acaba en FAILED aunque vaya bien; mira ALL OK)...'
$extra = @()
if ($ConAddons) {
    # Un addon con la ruta vacia se salta (-Connected '' cuando el jar no arranca con esta version de Create Fly).
    if ($DragonsPlus) {
        if (-not (Test-Path -LiteralPath $DragonsPlus)) { Write-Host "NO LANZO: no existe $DragonsPlus"; return }
        $extra += "-PwithDragonsPlus=$DragonsPlus"
        Write-Host "Con addon: $DragonsPlus"
    }
    if ($Connected) {
        if (-not (Test-Path -LiteralPath $Connected)) { Write-Host "NO LANZO: no existe $Connected"; return }
        $extra += "-PwithConnected=$Connected"
        Write-Host "Con addon: $Connected"
    }
}
& .\gradlew.bat runClientGameTest --offline --console=plain @extra *> $Log
Resumen
