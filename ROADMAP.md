# ROADMAP – Musikplayer

Abhakliste für alle geplanten Updates. Wird bei jedem Update mitgepflegt.

**Legende**
- `[x]` erledigt und auf dem Gerät bestätigt
- `[~]` im Code umgesetzt, Test auf dem Gerät steht noch aus
- `[ ]` offen

---

## Update 07 – Android-Standard-Musikplayer *(umgesetzt, Gerätetest offen)*

- [~] Die App erscheint bei „Öffnen mit“ für Audio-Dateien (MP3, FLAC, WAV, M4A, AAC, OGG, OPUS usw.) und kann dort mit „Immer“ als bevorzugte App gesetzt werden.
- [~] Die App meldet sich bei Android als Musik-App (`CATEGORY_APP_MUSIC`).
- [~] Eine von außen geöffnete Datei spielt **einzeln**, ohne Ordner-Warteschlange.
- [~] Liegt die Datei in der Bibliothek, läuft der Bibliothekstitel mit allen Aktionen (Herz, Playlist, Sperren, Umbenennen).
- [~] Liegt sie nicht in der Bibliothek, wird sie nur abgespielt und **nirgends gespeichert** (keine Bibliothek, Playlists, Zähler oder Fortsetzen). Titel, Interpret und Cover kommen aus der Datei. Im Player steht „Nicht in deiner Bibliothek“.
- [~] Nach dem Öffnen geht der Vollbild-Player auf. Die Zurück-Taste führt in den bisherigen Reiter.
- [~] Das Abspielen klappt auch, wenn „Zugriff auf alle Dateien“ noch nicht erteilt ist. Dann erscheint die Mini-Leiste unter dem Berechtigungshinweis.
- [~] Von außen geöffnete Lieder haben eigene Shuffle- und Wiederholen-Einstellungen. Ein „Alle wiederholen“ der Bibliothek lässt die Datei also nicht endlos laufen.
- [~] Wiedergabe fortsetzen: Die Play-Taste an Kopfhörer, Bluetooth oder Auto startet das zuletzt gespielte Bibliothekslied an der gemerkten Stelle samt seiner Liste, auch wenn die App nicht läuft. Ohne gespeicherten Stand startet „Alle Titel“.
- [~] Android 11+ bietet in den System-Mediensteuerungen „Zuletzt gespielt“ zum Fortsetzen an.
- [x] Sperrbildschirm, Benachrichtigung, Kopfhörer und Android Auto laufen über die vorhandene Media3-Sitzung. Das war schon vorher vorhanden, es wurde nichts daran geändert.

**Auf dem Gerät zu prüfen**
1. Im Dateimanager eine MP3 antippen → Musikplayer wird angeboten → „Immer“ wählen → nächste MP3 öffnet direkt.
2. Eine Audio-Datei aus WhatsApp/Telegram oder den Downloads öffnen. Das Lied muss spielen und darf nicht in der Bibliothek auftauchen.
3. Eine Datei öffnen, die schon in der Bibliothek liegt. Herz und Playlist müssen im Player sichtbar sein.
4. App wegwischen, dann die Play-Taste am Bluetooth-Kopfhörer drücken. Das letzte Lied muss weiterlaufen.
5. Ergebnis notieren: Bietet der Hersteller (z. B. Samsung) irgendwo eine „Standard-Musik-App“-Auswahl an? Android selbst hat dafür keinen eigenen Schalter.

---

## Update 08 – Jam, Teil A: Bibliotheken teilen *(als Nächstes)*

Ziel: Zwei Handys mit der App im selben WLAN finden sich und können Musik untereinander übernehmen.

- [ ] Andere Geräte mit der App im selben Netz automatisch finden. Pop-up: „Jemand in der Nähe – Jam erstellen / Jam beitreten“.
- [ ] Jam erstellen (Host) und beitreten (Gast), mit Bestätigung durch den Host.
- [ ] Die Bibliothek des anderen ansehen: Lieder, Alben, Interpreten, Playlists.
- [ ] Übernehmen („abonnieren“): einzelne Lieder, ganze Alben, ganze Interpreten, ganze Playlists.
  - Die Dateien werden vom anderen Handy heruntergeladen.
  - Metadaten und Zuordnungen kommen mit: Interpret, Album, Cover, Playlist-Zugehörigkeit.
  - Auf dem eigenen Handy wird alles nach den eigenen Regeln einsortiert.
- [ ] Duplikate werden **vor** dem Herunterladen erkannt, mit der vorhandenen Duplikat-Logik (Name, Größe, Länge). Bereits vorhandene Lieder werden nicht doppelt geladen, Playlists verweisen dann auf das eigene Exemplar.
- [ ] Übernommene Lieder erscheinen in einer eigenen, bearbeitbaren Liste, so wie „Neue Lieder“.
- [ ] Offene Punkte vor dem Start: Zielordner auf dem Handy, Sicherheit (nur nach Bestätigung, nur im lokalen Netz), Verhalten bei Abbruch.

## Update 09 – Jam, Teil B: gemeinsam hören *(später)*

- [ ] Der Gast hört, was der Host spielt, auch Lieder, die er selbst nicht besitzt (Streaming im WLAN).
- [ ] Gemeinsame Warteschlange: Jeder kann Lieder hinzufügen, starten und stoppen, Playlists starten und stoppen.
- [ ] Offene Entscheidung: Spielt nur der Host ab, oder spielen alle Handys synchron? Synchron ist deutlich aufwendiger und hat im WLAN immer etwas Versatz.

## Später – Download-Funktion

- [ ] Eigene Download-Liste in der App, bearbeitbar wie „Neue Lieder“.
- [ ] Download von Webseiten bzw. YouTube als MP3/WAV.
  - Hinweis: Das verstößt gegen die YouTube-Nutzungsbedingungen und ist in Deutschland rechtlich heikel (§ 95a UrhG, Umgehen technischer Schutzmaßnahmen). Vor der Umsetzung bewusst entscheiden.
  - Technik: Für MP3 braucht es einen eigenen Encoder (FFmpeg/LAME), der die APK vergrößert. WAV geht ohne Zusatz.

---

## Bereits vorhanden (Basis bis Update 06)

Siehe [README.md](README.md): Wiedergabe mit Equalizer, Sleep-Timer und Ausfaden, Bibliothek mit Interpreten, Alben, Playlists und Hörbüchern, Suche, Sperrliste mit zwei Stufen, Neue Lieder, Namensbereinigung, Umbenennen und Einsortieren, Stille überspringen, Duplikate, Cover-Suche, Android Auto.
