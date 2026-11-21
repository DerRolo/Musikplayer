# Musikplayer (werbefrei, nur lokale Lieder)

Android-Musikplayer für die Lieder auf dem Handy und der SD-Karte.

- Liest Musik wie der Dateimanager-Reiter „Musik“ (Sprachnachrichten, Aufnahmen, Klingeltöne werden ausgeblendet)
- Titel, Interpreten, Playlists, Warteschlange, Shuffle, Wiederholen
- Playlists nur in der App – es werden keine Dateien kopiert
- Sleep-Timer mit langsamem Ausblenden (Dauer und Ausblendzeit einstellbar)
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
