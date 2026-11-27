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

## Update 11 – Lautstärke angleichen (Normalisieren) *(umgesetzt, Gerätetest offen)*

- [~] Neue Aktion „Lautstärke angleichen …“ (orange). Sie steht überall gleich zur Verfügung: im Aktionsmenü jedes Liedes (Bibliothek, Interpret, Album, Playlist, Suche usw.), im großen Player und in der Mehrfachauswahl.
- [~] Messung über das ganze Lied nach EBU R128 (LUFS), Ziel −14 LUFS wie bei Spotify und YouTube. Gleichmäßige Änderung, kein Kompressor, mit Schutz vor Übersteuern (Spitze höchstens −1 dBFS).
- [~] Ein Lied: Erst wird gemessen und angezeigt (zum Beispiel „Gemessen −8,3 LUFS – zu laut, Änderung −6,0 dB“), dann bestätigst du. Mehrere Lieder laufen im Hintergrund, mit Fortschrittsleiste.
- [~] MP3: verlustfrei in der Datei (mp3gain-Verfahren, 1,5-dB-Schritte), exakt umkehrbar.
- [~] WAV: in der Datei umgerechnet.
- [~] Andere Formate: Angleichen beim Abspielen, die Datei bleibt unverändert. Leiser über die Player-Lautstärke, lauter über Androids LoudnessEnhancer. Der Sleep-Timer berücksichtigt das beim Ausblenden.
- [~] „Original wiederherstellen“ im selben Dialog.
- [~] **Vorhören:** Im Dialog wechselst du zwischen „Original“ und „Angeglichen“. Das Lied läuft an derselben Stelle weiter, nur die Lautstärke wechselt. Läuft das Lied noch nicht, wird es als Nächstes eingeschoben und ab einem Drittel gestartet, die Warteschlange bleibt. Die Datei ändert sich erst mit „Angleichen“. Beim Schließen des Dialogs gilt wieder die normale Lautstärke.
- [~] Die Datei wird sicher ersetzt: erst eine Kopie schreiben, dann tauschen. Bei einem Fehler bleibt das Original unberührt. Danach wird die Stille-Erkennung des Liedes neu gemessen.
- Lieder anderer im Jam lassen sich nicht angleichen, weil sie nicht bearbeitet werden dürfen.

**Auf dem Gerät zu prüfen**
1. Ein deutlich zu lautes und ein zu leises MP3 angleichen. Danach sollen beide ähnlich laut sein, auch in einem anderen Player.
2. „Original wiederherstellen“ → die Lautstärke ist wieder wie vorher.
3. Eine M4A oder FLAC angleichen → beim Abspielen in der App angepasst, die Datei bleibt unverändert.

## Bugfix – Shuffle aus stellt die Listen-Reihenfolge wieder her *(umgesetzt, Gerätetest offen)*

- [~] Fehler: Wurde ein Lied aus der Mitte einer Liste mit Shuffle gestartet, blieb nach „Shuffle aus“ die gemischte Reihenfolge stehen. Beim ersten Lied der Liste ging es, deshalb klappte es nur manchmal.
- [~] Jetzt gilt nach „Shuffle aus“ wieder die Reihenfolge der Liste, ausgehend vom laufenden Lied: weiter = das Lied, das in der Liste danach kommt, zurück = das davor.
- [~] „Shuffle an“ mischt alle übrigen Lieder neu, auch die, die in der Liste vor dem laufenden standen.
- [~] Eingereihte Lieder („Als Nächstes“) bleiben in beiden Fällen direkt hinter dem laufenden Lied.
- [~] Die Regel gilt überall gleich: Mini-Leiste, großer Player und Shuffle-Knöpfe auf Listen- und Playlist-Seiten.

## Update 08 – Jam, Teil A: Bibliotheken teilen *(umgesetzt, Gerätetest offen)*

- [~] Andere Handys mit der App im selben WLAN werden automatisch gefunden. Das geht nur, solange die App offen ist.
- [~] Pop-up überall in der App:
  - Ist jemand in der Nähe → „Jam erstellen“.
  - Hat jemand einen Jam gestartet → „Jam beitreten“.
- [~] **Beitreten ohne Bestätigung:** Jeder im WLAN kann einem laufenden Jam beitreten.
- [~] Der Host sieht auf der Jam-Seite, wer im Jam ist und wer sonst im WLAN ist. Er kann Leute entfernen, die dann diesem Jam nicht wieder beitreten können. Ein neuer Jam beginnt frisch.
- [~] Schalter „Im WLAN sichtbar“ (Optionen → Jam).
- [~] Die Bibliothek jedes anderen Mitglieds ansehen: Lieder, Alben, Interpreten, Playlists und Hörbücher, mit Suche und Covern.
- [~] **Komplett übernehmen** (Lied, Album, Interpret, Playlist, auch direkt aus dem Player):
  - Die Dateien kommen gleich an den richtigen Ort: `Music/<Interpret>/<Album>/`, ohne Album nach `Music/<Interpret>/`.
  - Interpret, Album, eigenes Cover und Playlist werden mit übernommen.
  - Lieblingssongs des anderen werden zu „Lieblingssongs von <Name>“.
  - Die Lieder stehen danach zusätzlich in „Neue Lieder“, sind dort aber schon fertig einsortiert.
- [~] Duplikate werden vorher erkannt: grüner Haken, nicht doppelt geladen, Playlists verweisen auf das eigene Exemplar.
- [~] Lieder anderer lassen sich im Jam nur anhören und übernehmen, nicht bearbeiten oder löschen.

## Update 09 – Jam, Teil B: gemeinsam hören *(umgesetzt, Gerätetest offen)*

- [~] **Eine gemeinsame Ausgabe** für den ganzen Jam: Wählbar sind alle verbundenen Ausgabegeräte aller Mitglieder, also das Handy des Hosts oder seine Bluetooth-Box, das Handy eines Gasts oder dessen Box. Jeder darf wählen.
- [~] Beim Wechsel auf ein anderes Handy zieht die Wiedergabe mit Warteschlange (bis 300 Lieder), Stelle und Play/Pause dorthin um, am alten Gerät wird gestoppt.
- [~] Alle anderen Handys sind Fernbedienung: Der Player zeigt, was an der Ausgabe läuft, und alle Tasten, Gesten und Menüs wirken dort.
- [~] Jeder kann Lieder, Alben und Playlists starten oder einreihen, aus der eigenen Bibliothek oder der der anderen. Lieder, die das abspielende Handy nicht hat, werden im WLAN gestreamt.
- [~] Ausgabe-Knopf im großen Player: Warteschlange öffnen, dann rechts oben. Er funktioniert auch ohne Jam, dann für die Geräte des eigenen Handys.
- [~] Verlässt das abspielende Mitglied den Jam (oder wird entfernt), geht die Ausgabe zurück ans Host-Handy.
- Bekannte Grenzen:
  - Beim Ausgabe-Wechsel entsteht eine kurze Pause.
  - Lieder eines Mitglieds, das den Jam verlässt, werden übersprungen.
  - Dateien von außen („Öffnen mit“) lassen sich während eines Jams nicht abspielen.

## Update 10 – Papierkorb *(umgesetzt, Gerätetest offen)*

- [~] Alles, was die App löscht (einzelne Lieder, Mehrfachauswahl, Duplikate, Aufräumen), kommt in den Papierkorb statt sofort weg.
- [~] Nach 30 Tagen wird endgültig gelöscht. Das passiert beim Start der App.
- [~] Optionen → Papierkorb:
  - Antippen spielt das Lied (wie überall), langes Drücken startet die Mehrfachauswahl.
  - Wiederherstellen geht einzeln, mehrfach oder komplett, endgültig löschen geht einzeln, mehrfach oder als „Leeren“.
- [~] Wiederherstellen an den alten Ort: Playlists, Favoriten, Interpret, Album, Cover und Wiedergabezähler sind wieder da. Ist der alte Name belegt, kommt „(2)“ dazu und alles wird umgestellt.
- [~] Direkt nach dem Löschen gibt es „Rückgängig“ in der Meldung. Im Player steht bei Papierkorb-Liedern „Im Papierkorb · Wiederherstellen“.
- Die Dateien liegen im App-Ordner auf demselben Speicher (SD-Karte bleibt SD-Karte) und erscheinen nicht in anderen Musik-Apps. Achtung: Wird die App deinstalliert, ist auch der Papierkorb weg.

**Auf dem Gerät zu prüfen (Handy + Tablet)**
1. Tablet öffnet die App → Pop-up „Jam beitreten“, sobald das Handy einen Jam erstellt hat → Beitritt ohne Bestätigung.
2. Handy hat die Bluetooth-Box verbunden. Das Tablet startet ein Lied → es läuft auf der Box.
3. Ausgabe auf „Dieses Handy“ bzw. „Handy von Tablet“ wechseln → die Musik zieht um und läuft an derselben Stelle weiter.
4. Tablet: Bibliothek des Handys → Album übernehmen → liegt danach unter `Music/<Interpret>/<Album>`, mit Cover und richtigem Interpreten.
5. Host entfernt das Tablet → das Tablet bekommt eine Meldung und kann nicht wieder beitreten.
6. Ein Lied löschen → Rückgängig. Ein Lied löschen → Optionen → Papierkorb → antippen (spielt) → Wiederherstellen → wieder in Bibliothek und Playlists.

## Später – Download-Funktion

- [ ] Download von Webseiten bzw. YouTube als MP3/WAV, mit eigener Download-Liste. **Bewusst noch nicht gebaut.**
  - Das Herunterladen von YouTube verstößt gegen die YouTube-Nutzungsbedingungen. In Deutschland hat das LG Hamburg 2021 entschieden, dass ein YouTube-Downloader eine wirksame technische Schutzmaßnahme umgeht (§ 95a UrhG).
  - Technik: Für MP3 braucht es einen eigenen Encoder (FFmpeg/LAME), der die APK vergrößert. WAV ginge ohne.
  - Für Musik aus dem Jam ist die Download-Liste schon gelöst: Übernommene Lieder landen in „Neue Lieder“.

---

## Bereits vorhanden (Basis bis Update 06)

Siehe [README.md](README.md): Wiedergabe mit Equalizer, Sleep-Timer und Ausfaden, Bibliothek mit Interpreten, Alben, Playlists und Hörbüchern, Suche, Sperrliste mit zwei Stufen, Neue Lieder, Namensbereinigung, Umbenennen und Einsortieren, Stille überspringen, Duplikate, Cover-Suche, Android Auto.
