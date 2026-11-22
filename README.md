# Musikplayer (werbefrei, nur lokale Lieder)

Android-Musikplayer für die Lieder auf dem Handy und der SD-Karte.

- Liest Musik wie der Dateimanager-Reiter „Musik“ (Sprachnachrichten, Aufnahmen, Klingeltöne werden ausgeblendet)
- Reiter: Titel, Interpreten (Kachel- oder Listenansicht), Playlists, Suche, Einstellungen
- Suche in Titeln und Interpreten (ohne Akzente/Groß-Kleinschreibung) plus globale Suche über alles
- Gedrückt-halten auf einen Titel startet die Mehrfachauswahl; auf einen Interpreten oder eine Playlist öffnet es ein Menü
  (alle abspielen, zufällig, als Nächstes, zur Warteschlange, zu Playlist)
- Playlists nur in der App – es werden keine Dateien kopiert; beim Hinzufügen bereits vorhandener Lieder
  kommt eine Warnung („Doppelte überspringen“ / „Trotzdem alle hinzufügen“)
- Warteschlange, Shuffle, Wiederholen; Tippen auf die Benachrichtigung öffnet die App
- Vollbild-Player: drehende Schallplatte mit Cover, echter Equalizer (Spektrum der gespielten Musik; Android verlangt dafür die Berechtigung „Audio aufnehmen“, es wird aber nicht das Mikrofon abgehört und nichts aufgenommen; ohne Berechtigung läuft eine Animation), Wischen links = nächstes, rechts = voriges Lied
- **Sleep-Timer:** stoppt nach X Minuten; die letzten 20 Sekunden blendet er immer automatisch aus
- **Ausfaden:** getrennt davon – die Lautstärke sinkt linear über 5 Min. bis 8 Std. auf 0 % (auch über Liedwechsel),
  danach stoppt die Musik; die Zeit läuft nur, solange Musik spielt
- Namen werden nur für Anzeige und Sortierung bereinigt: führende Tracknummern (01, 01., 01 -, 01_) und Unterstriche
  verschwinden, Namen wie 2Pac, 50 Cent, 10cc, 3 Doors Down bleiben; Dateien werden nie umbenannt
- Android Auto (Alle Titel / Interpreten / Playlists)
- Einstellungen:
  - **Interpreten & Ordner:** Ordner ohne Interpret im Tag prüfen, Ordnername als Interpret übernehmen oder eingeben
  - **Nach Interpret einsortieren:** verschiebt zugeordnete Lieder nach `<Speicher>/Music/<Interpret>` (SD-Karte bleibt SD-Karte)
  - **Duplikate:** ähnlicher Name + ähnliche Größe + gleiche Länge, „Rest markieren“, Löschen mit Bestätigung
- Löschen einzelner Dateien über das Titel-Menü, immer mit Bestätigung (kein Papierkorb)

## APK über GitHub bauen
1. Neues GitHub-Repository anlegen und den Inhalt dieses Ordners hochladen
   (auch den versteckten Ordner `.github` und die Datei `debug.keystore`).
2. Unter **Actions** startet der Bau automatisch (ca. 3–5 Min.).
3. Fertige APK: Reiter **Releases → „Musikplayer (neueste Version)“ → Musikplayer.apk**
   (oder unter Actions → Lauf → Artifacts).
4. APK auf dem Handy öffnen, „Installation aus unbekannten Quellen“ erlauben, installieren.
5. Beim ersten Start „Zugriff auf alle Dateien“ erlauben.

Updates: Code ändern, hochladen, neue APK laden und drüberinstallieren (gleiche Signatur).

## Android Auto
Die App meldet sich als Medien-App. Weil sie nicht aus dem Play Store kommt, muss in der
Android-Auto-App auf dem Handy die Entwicklereinstellung **„Unbekannte Quellen“** aktiviert sein:
Android Auto → Einstellungen → Version mehrfach antippen → Entwicklereinstellungen → Unbekannte Quellen.
