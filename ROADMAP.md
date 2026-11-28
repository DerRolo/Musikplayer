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

## Update 18 – Musik herunterladen (legal) *(umgesetzt, Gerätetest offen)*

- [~] **Musik herunterladen** (Optionen → direkt über „Neue Lieder“, und als ⬇ oben auf der Seite „Neue Lieder“).
  - Die App lädt selbst nichts herunter. Sie öffnet die Seite im **Standard-Browser**.
  - **Geprüfte Seiten:** Jamendo, Free Music Archive, Internet Archive (Audio), Bandcamp, ccMixter, Musopen.
  - **Hinweis „Was ist okay – und was nicht?“** steht auf der Seite und erscheint noch einmal vor dem Freischalten der Websuche.
- [~] **Schalter „Nur geprüfte Seiten“** (Standard: an). Dann ist die freie Websuche gesperrt. Ausschalten nur nach Bestätigung des Hinweises; danach gibt es ein Suchfeld (öffnet die Suche im Browser).
  - Ehrlich gesagt: Der Schalter sperrt nur, was die App öffnet. Im Browser selbst kann man weiterhin überall hin.
- [~] **Torrents** über eine Torrent-App. Ist eine installiert, gibt es „<App> öffnen“. Sonst schlägt die App **LibreTorrent** vor (kostenlos, Open Source, ohne Werbung; Play Store, auch auf F-Droid). Legale Torrents gibt es z. B. beim Internet Archive.
- [~] **Neue Downloads finden:** Kommst du zur App zurück, sucht sie im Download-Ordner (mit Unterordnern) nach neuen Audiodateien, meldet sie Android und zeigt „X neue Lieder heruntergeladen – siehe Neue Lieder“. Beim allerersten Start merkt sie sich nur den Zeitpunkt.
  - Bekannte Grenze: Lädt eine Torrent-App noch, kann ein halb fertiges Lied schon einmal gemeldet werden und nach dem Fertigwerden noch einmal.

**Test:** Optionen → Musik herunterladen → Jamendo → ein Lied herunterladen → zurück zur App → Meldung erscheint → Neue Lieder zeigt das Lied. Schalter ausschalten → Hinweis → Suchfeld erscheint. Ohne Torrent-App: „LibreTorrent installieren“ öffnet den Play Store.

## Update 17 – Kompakter Sortier-Knopf, Update ohne Nachfrage *(umgesetzt, Gerätetest offen)*

- [~] **Sortier-Knopf** überall nur noch als ⇅ mit Aufklapp-Pfeil, ohne Text. Das spart Platz, die Anzahl in der Bibliothek passt wieder.
  - Aufgeklappt hat jede Möglichkeit ein Symbol: 🎧 Meistgehört, A↓ Name/Titel, 👤 Interpret, 📅 Hinzugefügt, ⏱ Länge, ≡ Meiste Lieder bzw. Anzahl.
  - Die gewählte Möglichkeit ist markiert und zeigt ihre Richtung **↑/↓**.
  - Nochmal antippen dreht die Richtung um. Das Menü **bleibt dabei offen** und schließt sich erst beim Tippen daneben.
  - „Name A–Z“ und „Name Z–A“ sind jetzt **eine** Möglichkeit „Name“ mit Richtung. Das gilt für Interpreten, Alben, Playlists, Hörbücher und die Jam-Bibliothek.
  - In der Bibliothek geht es reihum: natürliche Richtung → umgekehrt → aus.
  - Auch Ordner, Neue Lieder und die geöffnete Playlist haben jetzt Symbole und das offene Menü.
  - Hinweis: Weil die Möglichkeiten neu zusammengefasst sind, startet die gemerkte Sortierung dieser Ansichten einmal neu mit „Meistgehört“.
- [~] **App-Update ohne Android-Nachfrage** (ab Android 12): Hat die App ihre Version selbst installiert, aktualisiert sie sich nach „Installieren“ ohne weitere Abfrage. Beim ersten In-App-Update fragt Android noch einmal. Ob Xiaomi seinen eigenen Scan trotzdem zeigt, entscheidet das Handy. Er lässt sich unter Einstellungen → Apps → System-App-Einstellungen → Sicherheit → Sicherheitsscan → „Vor Installation durchsuchen“ abschalten.

## Update 16 – Feste Leisten, Herz-Filter, Kachel-Bibliothek, Design *(umgesetzt, Gerätetest offen)*

- [~] **Feste Werkzeugleiste**, überall gleich (Bibliothek, Interpreten, Alben, Playlists, Hörbücher, Jam-Bibliothek):
  - Sie steht über der Liste statt in ihr und bleibt beim Scrollen, Filtern und beim Wechsel zwischen Kachel und Liste an derselben Stelle.
  - Links die Anzahl (immer einzeilig), rechts die Symbole, ganz rechts die Sortierung.
  - Der Sortier-Knopf hat eine feste Breite, damit sich beim Umsortieren nichts verschiebt.
- [~] Das Wort „Sortierung“ entfällt überall. Das Symbol ⇅ zeigt es, daneben steht nur die Art, zum Beispiel „Hinzugefügt ↓“.
- [~] **Interpreten:**
  - Der Shuffle-Knopf, der nur bei aktivem Stern auftauchte und die Leiste verschob, ist entfernt.
  - Der Stern bleibt jetzt auch bei Buchstabe ohne Treffer sichtbar. Vorher verschwand er mit der Liste. Statt der leeren Liste steht dann „Keine Favoriten-Interpreten mit „X““.
- [~] **Bibliothek**, gleicher Aufbau wie Interpreten:
  - **Herz-Filter** (nur Lieblingssongs).
  - ▶ **der Reihe nach abspielen** und ⤮ **zufällig abspielen**, jetzt klar als zwei Knöpfe.
  - **Kachel- oder Listenansicht** (Cover, Titel, Interpret), wird gemerkt.
- [~] **Design** (Optionen → Design):
  - **Hell / Dunkel / Wie System**.
  - **Farbvorlagen** mit Vorschau zum Antippen: Musikplayer (Standard, warmes Dunkel mit Orange), Ozean, Wald, Beere, Lavendel, Sonnenuntergang, Graphit, Mitternacht (reines Schwarz), ab Android 12 auch die Systemfarben. Die Wahl wirkt sofort.
  - Das bisherige fast schwarze Dunkel kam von den Systemfarben. Neuer Standard ist die Vorlage „Musikplayer“.
- [~] **Profilbild drehen** (↻, jeweils um 90°).
- Vorlagen aus dem Internet laden ist nicht eingebaut. Die App bringt ihre Vorlagen mit, weitere lassen sich leicht ergänzen.

## Update 15 – Sicherung für eine Neuinstallation *(umgesetzt, Gerätetest offen)*

- [~] **Automatische Sicherung**: Die App sichert ihre Daten in den Ordner `Musikplayer-Sicherung` im Handyspeicher. Der bleibt beim Deinstallieren erhalten.
  - Gesichert werden Playlists, Hörbücher, Lieblingssongs, Wiedergabezahlen, Interpreten- und Album-Zuordnungen, eigene Cover, Sperrliste, Sortierungen, Einstellungen und Profil.
  - Das passiert nach Änderungen im Hintergrund (höchstens alle 10 Minuten) und beim Verlassen der App. Die letzten 5 Sicherungen bleiben, jede ist eine ZIP-Datei.
- [~] **Neuinstallation:** Beim ersten Start fragt die App „Sicherung gefunden – wiederherstellen?“.
  - „Wiederherstellen“ richtet alles wie vorher ein und startet die App neu.
  - „Neu anfangen“ ignoriert die Sicherung.
  - Solange die Frage nicht beantwortet ist, wird nicht automatisch gesichert. So kann ein leerer Neustart keine alten Sicherungen verdrängen.
- [~] **Optionen → Sicherung** (ganz unten): Erklärung, „Automatisch sichern“ an/aus, **„Jetzt sichern“**, Liste der Sicherungen mit „Wiederherstellen“, „Alle Sicherungen löschen“. Vor einem Wiederherstellen wird der aktuelle Stand selbst noch gesichert.
- [~] **Papierkorb** liegt jetzt ebenfalls im Sicherungsordner (`Musikplayer-Sicherung/Papierkorb`, auf SD-Karten im gleichen Ordner dort) und übersteht damit ein Neuinstallieren. Er ist weiterhin für andere Musik-Apps unsichtbar. Einträge, deren Datei fehlt, räumt die App still weg.
- Voraussetzung: Die Musikdateien liegen nach der Neuinstallation an denselben Stellen. Die Zuordnungen hängen an den Dateipfaden.

## Update 14 – Sortierung merken, App-Update aus GitHub, Build-Fix *(umgesetzt, Gerätetest offen)*

- [~] **Build-Fehler behoben:** In der neuen Warteschlange wurde die Zieh-Funktion falsch aufgerufen (Kotlin-Fehler `detectDragGesturesAfterLongPress`).
- [~] **Sortierung wird gemerkt**, auch nach einem Neustart: Interpreten, Alben, Playlists, Hörbücher, geöffnete Playlist, Ordner, Neue Lieder und jeder Reiter der Jam-Bibliothek. Die Bibliothek merkte sich die Sortierung schon vorher.
- [~] **App-Update direkt aus der App:**
  - Beim Start sucht die App im Hintergrund in den GitHub-Releases nach einer neueren Version. Das lässt sich in Optionen → App-Update abschalten.
  - Gibt es eine, erscheint „Update bereit“ mit den Neuerungen und „Installieren“ / „Später“.
  - „Installieren“ lädt die APK mit Fortschrittsanzeige. Danach zeigt Android seine Bestätigung, deine Daten bleiben erhalten.
  - Beim ersten Mal fragt Android, ob die App Updates installieren darf.
  - Optionen → App-Update zeigt die installierte Version und hat „Jetzt suchen“.
- [~] **Versionsnummer:** Der Workflow gibt jeder APK die fortlaufende Build-Nummer mit (Version 1.0.<Nummer>) und trägt das Repository ein. Daran erkennt die App, ob ein Release neuer ist.
- [~] Workflow: `actions/checkout` und `actions/setup-java` auf v5 (behebt die Deprecation-Warnungen).
- Voraussetzungen und Grenzen:
  - Das Repository muss öffentlich sein, sonst liefert GitHub ohne Anmeldung keine Releases.
  - Die APK muss weiter mit der festen `debug.keystore` signiert sein. Das ist schon so.
  - Erst Versionen ab diesem Update kennen ihre Nummer. Die jetzt installierte Version zeigt das erste neue Release deshalb sicher als Update an.

## Update 13 – Einheitliche Bibliothek, gemeinsame Suche, Jam-Bibliothek mit Sortierung *(umgesetzt, Gerätetest offen)*

- [~] **Bibliothek mit drei Reitern:** Bibliothek · Interpreten · **Alben** (neu). Der orange Umschalter zeigt den aktiven Reiter. Alben haben Suche, Buchstabenleiste, Kachel/Liste und Sortierung (Meistgehört, Name, Interpret, Meiste Lieder, Zuletzt hinzugefügt). Tippen öffnet das Album, Zurück führt an dieselbe Stelle in „Alben“.
- [~] **Eine Suche für alles:** Das Suchfeld oben in der Bibliothek zeigt genau dasselbe wie der Such-Reiter, mit Vorschlägen, Interpreten, Alben, Playlists, Hörbüchern und Titeln. Zurück führt wieder zur Suche. Album-Treffer öffnen jetzt direkt das Album statt nur den Interpreten.
- [~] **Gleicher Aufbau in allen Reitern** (Interpreten, Alben, Playlists, Hörbücher):
  1. Suchfeld über die ganze Breite.
  2. Oranger Knopf: der Umschalter bzw. „+ Neue Playlist“ / „+ Neues Hörbuch / Podcast“.
  3. Buchstabenleiste (Buchstaben ohne Treffer ausgegraut).
  4. Zeile mit Anzahl · Stern (nur Interpreten) · Kachel/Liste · Sortierung.
- [~] Die Lupe bei Playlists und Hörbüchern entfällt. Dort sucht das Feld nur in Playlists bzw. nur in Hörbüchern.
- [~] **Standard-Sortierung „Meistgehört“** bei Interpreten, Alben, Playlists und Hörbüchern.
- [~] **Jam – Bibliothek der anderen:**
  - Die Scroll-Position bleibt erhalten: Album, Interpret oder Playlist öffnen, Zurück (oben links oder Handy-Taste), und man ist wieder genau dort. Jeder Reiter merkt sich seine Stelle, auch die Jam-Seite selbst.
  - Buchstabenleiste wie in der eigenen Bibliothek.
  - Sortierung: „Meistgehört von <Name>“ (Standard), Name, Interpret, Länge, Zuletzt hinzugefügt bzw. Meiste Lieder.
  - Die Wiedergabezahlen des anderen werden angezeigt („12× gehört“), nur zum Entdecken. Beim Übernehmen kommen sie nicht mit.
- Kleinigkeit: Der Leertext bei Playlists sprach von Hörbüchern, das ist korrigiert.
- **Offen zur Entscheidung:** Der Such-Reiter unten ist jetzt doppelt, weil die Bibliothek dasselbe kann. Vorschlag: ihn durch einen **Jam-Reiter** ersetzen und den Jam aus den Optionen dorthin holen.

## Update 12 – Jam-Feinschliff nach dem ersten Test *(umgesetzt, Gerätetest offen)*

- [~] **Unterbrechung (Anruf, Video in einer anderen App):** Ist die Musik an der Ausgabe dadurch angehalten, zeigen alle „Pause“, und jeder kann sie mit Play wieder starten. Der Player wird dabei neu angestoßen. Das gilt auch ohne Jam. Solange ein Anruf läuft, gibt Android den Ton nicht frei.
- [~] **Nach dem Rauswerfen:** Will jemand wieder beitreten, bekommt der Host eine Anfrage („Beitreten lassen“ / „Ablehnen“). Alle anderen treten weiterhin ohne Bestätigung bei.
- [~] **Bearbeiten im Jam gesperrt**, für alle im Jam (Host und Gäste):
  - Gesperrt sind: Datei bearbeiten, Interpret zuordnen, Cover ändern, Lautstärke angleichen, Sperren, Löschen, Dateinamen übernehmen (oranger Speichern-Knopf), Album umbenennen, Interpreten-, Playlist- und Lied-Cover, Papierkorb wiederherstellen oder löschen.
  - Ebenso die Werkzeuge in den Optionen: Interpreten & Ordner, Einsortieren, Dateinamen übernehmen, Duplikate, Aufräumen, Namen-Filter.
  - Im großen Player und in der Mini-Leiste sind die Knöpfe herausgenommen. In Menüs und der Mehrfachauswahl sind die Punkte ausgeblendet, das Sperren-Schnellsymbol in den Liedzeilen ist ausgegraut.
  - Zusätzlich sperrt das ViewModel alle diese Funktionen zentral.
  - Erlaubt bleiben: Hören, Herz, Playlists, Warteschlange, Übernehmen.
- [~] **Developer Options → „Allow editing in Jam“** (Standard aus). Wirkt nur, wenn **alle** im Jam es eingeschaltet haben.
- [~] **Warteschlange neu**, überall gleich, nicht nur im Jam:
  - Jede Zeile zeigt Cover, Titel und Interpret · Album · Länge.
  - Gedrückt halten und schieben ändert die Reihenfolge, wie in Playlists. Tippen springt zum Lied.
  - Drei-Punkte-Menü: „Jetzt abspielen“, „Aus der Warteschlange entfernen“, im Jam zusätzlich „Übernehmen“ (schon vor dem Hören).
  - Pfeile und Kreuz entfallen. Eingereihte Lieder sind mit ● markiert.
  - Im Jam steht dabei „eingereiht von …“, und Gäste können die Warteschlange genauso sortieren und bearbeiten.
- [~] **Wer hat es eingereiht:** Jedes Jam-Lied merkt sich, wer es gestartet oder eingereiht hat. Bisher stand dort immer der Besitzer der Datei.
- [~] **Sleep-Timer und Ausfaden** gelten im Jam für die gemeinsame Ausgabe, egal wer sie startet. Die Restzeit steht auf der Jam-Seite.
- [~] **Lautstärke:** bestimmt das Gerät, das abspielt (wie bei Spotify). Es gibt keine Änderung, so ist es gewollt.
- [~] **Hörbücher** haben in der Bibliothek der anderen einen eigenen Reiter und lassen sich übernehmen (als Hörbuch-Liste).
- [~] **Profil** (Optionen → Profil): eigener Name (Vorgabe Gerätename) und Profilbild. Andere sehen beides im Pop-up, in der Mitgliederliste und bei „eingereiht von“.
- [~] **Vollsperre auch im Jam:**
  - Gesperrte Lieder (orange und rot) teilt kein Handy, sie lassen sich weder sehen noch laden noch streamen.
  - Was man selbst voll gesperrt hat (rot, also Interpreten oder dieselben Lieder), sieht man auch in der Bibliothek der anderen nicht.
- Shuffle im Jam: Gemischt wird die Liste (Playlist, Album …), von Hand eingereihte Lieder (●) bleiben in ihrer Reihenfolge vorne, wie bei Spotify. Werden fast nur einzelne Lieder eingereiht, ändert Shuffle deshalb kaum etwas.

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

- [x] Legale Downloads über geprüfte Seiten und Torrent-App → siehe Update 18.
- [ ] Download von YouTube als MP3/WAV. **Bewusst nicht gebaut.**
  - Das Herunterladen von YouTube verstößt gegen die YouTube-Nutzungsbedingungen. In Deutschland hat das LG Hamburg 2021 entschieden, dass ein YouTube-Downloader eine wirksame technische Schutzmaßnahme umgeht (§ 95a UrhG).
  - Technik: Für MP3 braucht es einen eigenen Encoder (FFmpeg/LAME), der die APK vergrößert. WAV ginge ohne.
  - Für Musik aus dem Jam ist die Download-Liste schon gelöst: Übernommene Lieder landen in „Neue Lieder“.

---

## Bereits vorhanden (Basis bis Update 06)

Siehe [README.md](README.md): Wiedergabe mit Equalizer, Sleep-Timer und Ausfaden, Bibliothek mit Interpreten, Alben, Playlists und Hörbüchern, Suche, Sperrliste mit zwei Stufen, Neue Lieder, Namensbereinigung, Umbenennen und Einsortieren, Stille überspringen, Duplikate, Cover-Suche, Android Auto.
