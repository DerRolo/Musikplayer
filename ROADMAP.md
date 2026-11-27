# ROADMAP – Musikplayer

Abhakliste für alle geplanten Updates. Wird bei jedem Update mitgepflegt.

**Legende**
- `[x]` erledigt und auf dem Gerät bestätigt
- `[~]` im Code umgesetzt, Test auf dem Gerät steht noch aus
- `[ ]` offen

---

## Update 07 – Android-Standard-Musikplayer *(umgesetzt, Gerätetest läuft)*

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

## Update 08 – Jam, Teil A: Bibliotheken teilen *(umgesetzt, Gerätetest offen)*

- [~] Andere Handys mit der App im selben WLAN werden automatisch gefunden. Das geht nur, solange die App offen ist.
- [~] Pop-up überall in der App:
  - Ist jemand in der Nähe → „Jam erstellen“.
  - Hat jemand einen Jam gestartet → „Jam beitreten“.
  - „Später“ blendet das Pop-up für diese Lage aus.
- [~] Jam erstellen (Host) und beitreten (Gast). Der Host muss jede Beitrittsanfrage annehmen.
- [~] Schalter „Im WLAN sichtbar“ (Optionen → Jam). Ist er aus, wird man nicht gefunden und sucht auch selbst nicht.
- [~] Die Bibliothek jedes anderen Mitglieds ansehen: Lieder, Alben, Interpreten, Playlists und Hörbücher, mit Suche und Covern.
- [~] Übernehmen von einzelnen Liedern, ganzen Alben, ganzen Interpreten und ganzen Playlists (auch Hörbuch-Listen), auch direkt aus dem Player.
  - Die Dateien kommen nach `<Speicher>/Music/<Interpret>/`, wie beim Einsortieren.
  - Interpret, Album und eigenes Cover werden mit übernommen.
  - Playlists werden angelegt oder ergänzt, wenn es schon eine mit dem Namen gibt. Die Lieblingssongs des anderen werden zu „Lieblingssongs von <Name>“.
- [~] Duplikate werden vorher erkannt, mit denselben Regeln wie die Duplikat-Suche. Grüner Haken = hast du schon. Vorhandene Lieder werden nicht neu geladen, Playlists verweisen dann auf das eigene Exemplar.
- [~] Übernommene Lieder erscheinen in „Neue Lieder“. Dort kannst du sie wie gewohnt bearbeiten, einsortieren und als gesehen markieren.
- [~] Fortschritt in der Jam-Leiste und auf der Jam-Seite. Am Ende kommt eine Meldung wie „12 übernommen, 3 schon vorhanden“.
- Hinweis: „Abonnieren“ ist ein einmaliges Übernehmen. Ändert der andere später seine Playlist, wird das nicht automatisch nachgezogen. Nochmal übernehmen ergänzt die neuen Lieder.

## Update 09 – Jam, Teil B: gemeinsam hören *(umgesetzt, Gerätetest offen)*

- [~] Alle im Jam hören, was der Host spielt, synchron auf dem eigenen Handy. Das klappt auch bei Liedern, die man selbst nicht hat, per Streaming im WLAN. Eigene Lieder spielt jeder direkt von der eigenen Datei.
- [~] Jeder darf steuern: Play/Pause, weiter, zurück, spulen, Shuffle, Wiederholen. Die Bedienung ist die normale App-Bedienung, als Gast geht sie automatisch an den Host.
- [~] Jeder kann Lieder, Alben und Playlists starten oder einreihen: aus der eigenen Bibliothek mit den gewohnten Gesten und Menüs, aus der Bibliothek der anderen über die Jam-Seite. Der Host spielt dann auch Lieder, die nur ein Gast hat.
- [~] Die Jam-Seite zeigt: läuft gerade, als Nächstes, wer dabei ist, von wem welches Lied kommt.
- [~] Gast: Player wegwischen oder „Jam verlassen“ beendet die Teilnahme. Host: „Jam beenden“. Wer die App aus den letzten Apps wischt, verlässt den Jam ebenfalls.
- Bekannte Grenzen:
  - Gleichlauf im WLAN meist auf etwa eine halbe Sekunde genau. Beim Liedwechsel ist der Gast bis zu einer Sekunde später dran.
  - Verlässt ein Gast den Jam, springt der Host bei dessen Liedern zum nächsten.
  - Gäste sehen die Jam-Warteschlange auf der Jam-Seite, nicht in der normalen Warteschlange.
  - Dateien, die der Host von außen geöffnet hat („Öffnen mit“), lassen sich nicht teilen.

**Auf dem Gerät zu prüfen (zwei Handys im selben WLAN)**
1. Beide Apps öffnen → bei einem kommt das Pop-up „Jam erstellen“ → erstellen → beim anderen „Jam beitreten“ → Host nimmt an.
2. Host spielt ein Lied → Gast hört es mit, Pause und Spulen auf beiden Seiten.
3. Gast startet ein eigenes Lied → läuft bei beiden.
4. Gast öffnet Optionen → Jam → Bibliothek des Hosts → ein Album übernehmen → Lieder liegen danach unter `Music/<Interpret>` und stehen in „Neue Lieder“. Grüne Haken bei Liedern, die schon da waren.
5. Eine Playlist übernehmen → Playlist ist danach beim Gast vorhanden, mit Cover und richtigen Interpreten.

## Später – Download-Funktion

- [ ] Download von Webseiten bzw. YouTube als MP3/WAV, mit eigener Download-Liste. **Bewusst noch nicht gebaut.**
  - Das Herunterladen von YouTube verstößt gegen die YouTube-Nutzungsbedingungen. In Deutschland hat das LG Hamburg 2021 entschieden, dass ein YouTube-Downloader eine wirksame technische Schutzmaßnahme umgeht (§ 95a UrhG).
  - Technik: Für MP3 braucht es einen eigenen Encoder (FFmpeg/LAME), der die APK vergrößert. WAV ginge ohne.
  - Für Musik aus dem Jam ist die Download-Liste schon gelöst: Übernommene Lieder landen in „Neue Lieder“.

---

## Bereits vorhanden (Basis bis Update 06)

Siehe [README.md](README.md): Wiedergabe mit Equalizer, Sleep-Timer und Ausfaden, Bibliothek mit Interpreten, Alben, Playlists und Hörbüchern, Suche, Sperrliste mit zwei Stufen, Neue Lieder, Namensbereinigung, Umbenennen und Einsortieren, Stille überspringen, Duplikate, Cover-Suche, Android Auto.
