# Noten-Tracker

Eine kleine Desktop-App, um im Studium den Überblick über die eigenen Noten zu behalten.
Entstanden für Wirtschaftsinformatik an der HAW Landshut: Notenblatt als PDF hochladen, fertig –
die App liest alle Module aus und rechnet den Rest.

## Was die App kann

- **Notenblatt importieren** – PDF aus dem Portal auswählen, Vorschau prüfen, übernehmen.
  Beim nächsten Import werden nur Änderungen übernommen, nichts doppelt.
- **Module verwalten** – von Hand hinzufügen, bearbeiten, löschen; filtern nach Semester, Status
  oder Kategorie, inklusive Schnitt und ECTS für die gefilterte Auswahl.
- **Kennzahlen** – gewichteter Notenschnitt, gesammelte ECTS, bestandene Module.
- **Diagramme** – Notenverlauf, Notenverteilung und ECTS-Fortschritt bis zum Abschluss.
- **Zielschnitt-Rechner** – „Ich will eine 2,5 – was brauche ich noch?“
- **Hell & Dunkel** – oder automatisch passend zu Windows.

## Loslegen

Du brauchst **IntelliJ IDEA** und **Java 21**.

1. In IntelliJ **File → Open…** und diesen Ordner öffnen.
2. `Launcher.java` öffnen und auf den grünen ▶ klicken.

Das war's. Die Meldung „Unsupported JavaFX configuration“ in der Konsole kannst du ignorieren.

**Tests:** Rechtsklick auf `src/test/java` → *Run 'All Tests'*.

## Als Windows-App installieren

```
mvn clean package -P exe
powershell -ExecutionPolicy Bypass -File install-app.ps1
```

Der erste Befehl baut die App (mit eigener Java-Laufzeit, läuft also auch ohne installiertes Java),
der zweite installiert sie und legt eine Verknüpfung auf dem Desktop und im Startmenü an.
In IntelliJ geht das erste auch per Klick: im Maven-Fenster das Profil `exe` anhaken, dann
*clean* und *package*.

> **Windows blockiert die App?** Wenn *Smart App Control* aktiv ist, lässt Windows die unsignierte
> `NotenTracker.exe` nicht starten. Nutze einfach die Verknüpfung – sie startet die App über die
> signierte Java-Laufzeit, das funktioniert.

## Gut zu wissen

- **Deine Daten** liegen unter `%APPDATA%\NotenTracker\noten.db` und bleiben bei Updates erhalten.
- **Der Notenschnitt** wird wie im Notenblatt mit der Spalte *Gewichtung* berechnet. Unbenotete Module
  (z. B. Studium Generale) zählen nur zu den ECTS, nicht bestandene gar nicht. Die Gesamtnote wird
  nach der ersten Nachkommastelle abgeschnitten (3,15 → 3,1).
- **Ordner nicht zu tief verschachteln** – Windows mag keine Pfade über 260 Zeichen.

## Unter der Haube

Java 21 · JavaFX 23 · Apache PDFBox · SQLite · JUnit 5 · Maven · jpackage

```
de.notentracker
├── model        Module, Noten, Semester
├── persistence  SQLite-Datenbank
├── parser       PDF auslesen und Notenblatt verstehen
├── service      Notenschnitt, Zielschnitt, Diagramme, Import
└── ui           Oberfläche und styles.css (alle Farben stehen dort oben)
```

**Anderes Notenblatt-Layout?** `PdfTextDump` mit der PDF starten, um zu sehen, wie der Text
ausgelesen wird. Bei einem ähnlichen Layout reicht es meist, die Spaltennamen in
`HawLandshutParser` anzupassen; sonst einen eigenen `GradeSheetParser` schreiben und in
`GradeSheetParsers` eintragen.
