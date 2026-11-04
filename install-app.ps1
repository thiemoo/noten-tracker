# Installiert die mit "mvn clean package -P exe" gebaute App fuer den aktuellen Benutzer.
#
# Warum nicht einfach NotenTracker.exe starten? Windows 11 "Smart App Control" blockiert Programme ohne
# vertrauenswuerdige Signatur - und der von jpackage erzeugte Starter NotenTracker.exe ist unsigniert.
# Die mitgelieferte Java-Laufzeit (runtime\bin\javaw.exe) ist dagegen von der Eclipse Foundation signiert.
# Die Verknuepfungen starten deshalb direkt javaw.exe mit unserem Programm.
#
# Aufruf (im Projektordner):  powershell -ExecutionPolicy Bypass -File install-app.ps1

$ErrorActionPreference = 'Stop'

$source = Join-Path $PSScriptRoot 'target\jpackage\dist\NotenTracker'
$target = Join-Path $env:LOCALAPPDATA 'Programs\NotenTracker'
$icon = Join-Path $PSScriptRoot 'src\main\packaging\icon.ico'

if (-not (Test-Path (Join-Path $source 'runtime\bin\javaw.exe'))) {
    throw "Keine gebaute App gefunden unter $source - bitte zuerst 'mvn clean package -P exe' ausfuehren."
}
if (Get-Process NotenTracker, javaw -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$target*" }) {
    throw 'Noten-Tracker laeuft noch - bitte zuerst schliessen.'
}

# 1. App kopieren (vorhandene Version wird ersetzt)
robocopy $source $target /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) {
    throw "Kopieren fehlgeschlagen (robocopy-Code $LASTEXITCODE)."
}
Copy-Item $icon (Join-Path $target 'NotenTracker.ico') -Force

# 2. Verknuepfungen auf Desktop und im Startmenue
$shell = New-Object -ComObject WScript.Shell
$desktop = [Environment]::GetFolderPath('Desktop')
$startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) 'Noten-Tracker.lnk'
foreach ($path in @((Join-Path $desktop 'Noten-Tracker.lnk'), $startMenu)) {
    $link = $shell.CreateShortcut($path)
    $link.TargetPath = Join-Path $target 'runtime\bin\javaw.exe'
    $link.Arguments = '-cp "' + (Join-Path $target 'app\*') + '" de.notentracker.Launcher'
    $link.WorkingDirectory = $target
    $link.IconLocation = (Join-Path $target 'NotenTracker.ico') + ',0'
    $link.Description = 'Noten-Tracker'
    $link.Save()
}

Write-Host "Installiert nach $target"
Write-Host 'Verknuepfungen: Desktop und Startmenue ("Noten-Tracker")'
