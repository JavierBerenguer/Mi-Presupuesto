<#
.SYNOPSIS
  Delegar una ficha de tarea (docs/tasks/T-XXX.md) a Codex CLI en una rama y worktree propios.

.EXAMPLE
  .\scripts\delegate-codex.ps1 -Task T-001
  .\scripts\delegate-codex.ps1 -Task T-001 -Slug pantalla-cuentas
#>
param(
  [Parameter(Mandatory = $true)][string]$Task,
  [string]$Slug = "",
  [string]$Base = "main"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$taskFile = "docs/tasks/$Task.md"
if (-not (Test-Path $taskFile)) { throw "No existe la ficha $taskFile" }
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) { throw "Codex CLI no está instalado (npm i -g @openai/codex)" }
if (-not (Test-Path ".git")) { throw "Esto no es un repositorio git. Ejecuta 'git init' y haz un primer commit." }
git rev-parse --verify $Base *> $null
if ($LASTEXITCODE -ne 0) { throw "La rama base '$Base' no existe o no tiene commits." }

$name = $Task.ToLower()
if ($Slug) { $name = "$name-$Slug" }
$branch = "codex/$name"
$worktree = Join-Path (Split-Path $root -Parent) "worktrees\$name"

if (-not (Test-Path $worktree)) {
  git worktree add -b $branch $worktree $Base
  if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el worktree." }
}

$report = Join-Path $root "docs/tasks/$Task.report.md"
$prompt = @"
Lee AGENTS.md y después docs/tasks/$Task.md. Implementa exactamente esa ficha, respetando el alcance y las zonas protegidas. Ejecuta los comandos de verificación que pide la ficha y termina con el informe descrito en ella, con resultados reales.
"@

Write-Host "Rama: $branch"
Write-Host "Worktree: $worktree"
Write-Host "Informe: $report"

codex exec --cd $worktree --sandbox workspace-write --output-last-message $report $prompt

Write-Host ""
Write-Host "Codex ha terminado. Revisa el diff antes de integrar:"
Write-Host "  git diff $Base...$branch"
