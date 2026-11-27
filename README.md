# Musikplayer

Werbefreier Android-Musikplayer für Lieder, die auf dem Handy oder der SD-Karte liegen. Er sieht aus und bedient sich ähnlich wie Spotify, braucht aber kein Konto und kein Internet. Nur die Cover-Suche greift auf das Netz zu, der Jam nur auf das eigene WLAN.

Gebaut mit Kotlin, Jetpack Compose und Media3. Die APK baut GitHub Actions.

## Was die App besonders macht

Die App ist nicht nur ein Player, sondern auch ein Aufräumwerkzeug für eine unordentliche lokale Sammlung. Sie erkennt, was in den Dateien steckt, und hilft dir beim Ordnen.

- Namen, Interpreten, Alben und Duplikate werden erkannt und bereinigt.
- Dateien lassen sich auf Wunsch umbenennen und in Interpreten-Ordner einsortieren.
- Stille am Anfang und Ende eines Liedes wird übersprungen, ohne die Datei zu verändern.
- Wiedergabe, Listen und Bedienung sind in der ganzen App gleich.

## Wiedergabe

- Hintergrundwiedergabe mit Benachrichtigung, Warteschlange, Shuffle, Wiederholen (aus, alle, eines).
- Vollbild-Player mit drehender Schallplatte, echtem Equalizer und Wischen für nächstes und voriges Lied.
- Der Equalizer zeigt das Spektrum der gespielten Musik. Dafür verlangt Android die Berechtigung „Audio aufnehmen“. Das Mikrofon wird nicht abgehört, ohne die Berechtigung läuft eine Animation.
- Mini-Leiste, Android Auto (Alle Titel, Interpreten, Playlists) und Cover in der Auto-Anzeige.
- **Sleep-Timer:** stoppt nach X Minuten und blendet die letzten 20 Sekunden immer aus.
- **Ausfaden:** getrennt davon. Die Lautstärke sinkt linear über 5 Minuten bis 8 Stunden auf null, auch über Liedwechsel hinweg. Die Zeit läuft nur, solange Musik spielt.

## Als Standard-Musikplayer

- **Öffnen mit:** Tippst du im Dateimanager, in den Downloads oder in einem Messenger auf eine Audio-Datei (MP3, FLAC, WAV, M4A, AAC, OGG, OPUS), bietet Android den Musikplayer an. Mit „Immer“ öffnen solche Dateien künftig direkt hier.
- **Nur dieses eine Lied:** Eine von außen geöffnete Datei spielt allein, ohne Ordner-Warteschlange. Liegt sie in deiner Bibliothek, hast du alle Aktionen wie Herz und Playlist. Sonst wird sie nur abgespielt und nirgends gespeichert („Nicht in deiner Bibliothek“). Titel, Interpret und Cover kommen dann aus der Datei.
- **Fortsetzen:** Die Play-Taste an Kopfhörer, Bluetooth oder im Auto startet das zuletzt gespielte Lied an der gemerkten Stelle, auch wenn die App gerade nicht läuft.
- Android selbst hat keinen eigenen Schalter „Standard-Musik-App“ wie beim Browser. Die bevorzugte App wird über „Öffnen mit → Immer“ festgelegt. Manche Hersteller bieten zusätzliche Auswahlmöglichkeiten.

## Jam (zusammen hören im WLAN)

Unter **Optionen → Jam**, oder über das Pop-up, sobald jemand mit der App im selben WLAN ist.

- **Jam erstellen oder beitreten** wie bei Spotify. Der Host nimmt jede Anfrage an, ohne Bestätigung kommt niemand an deine Musik.
- **Zusammen hören:** Alle hören synchron, was der Host spielt, auch Lieder, die sie selbst nicht haben (Streaming im WLAN). Jeder kann starten, pausieren, spulen, weiterspringen und Lieder oder Playlists einreihen, mit der ganz normalen Bedienung der App.
- **Musik übernehmen:** Die Bibliothek der anderen ansehen und Lieder, Alben, Interpreten oder ganze Playlists aufs eigene Handy holen. Interpret, Album, Cover und Playlist-Zuordnung kommen mit, die Dateien landen in `Music/<Interpret>`. Lieder, die du schon hast (gleiche Regeln wie die Duplikat-Suche), werden nicht doppelt geladen. Neues erscheint in „Neue Lieder“.
- **Sichtbarkeit:** Mit „Im WLAN sichtbar“ schaltest du das Finden und die Pop-ups ab.
- Für das Streaming im lokalen Netz erlaubt die App unverschlüsselte Verbindungen (`http`). Das betrifft nur Verbindungen zwischen den Handys im WLAN, der Zugang ist durch einen Jam-Schlüssel geschützt.

## Bibliothek und Ordnung

- **Reiter:** Bibliothek, Interpreten (Liste oder Kacheln, mit A–Z-Leiste), Playlists, Suche, Einstellungen.
- **Interpreten:** eigene Seite mit Profilbild, „Alle Songs abspielen“, Shuffle, Meistgehört und Alben. Mit einem Stern markierst du Favoriten-Interpreten und kannst die Liste darauf filtern.
- **Playlists und Hörbücher/Podcasts:** getrennte Listen, ineinander umwandelbar. Alles liegt nur in der App, es werden keine Dateien kopiert. Lieblingssongs sind eine eigene Liste mit Herz.
- **Suche:** sucht live in Titeln, Interpreten und Playlists, ohne Rücksicht auf Akzente und Groß- oder Kleinschreibung.
- **Sperrliste mit zwei Stufen:** Orange sperrt nur aus Bibliothek und Shuffle, Rot (Vollsperre) auch aus der Suche. Gesperrt werden können Lieder, Interpreten und Ordner.
- **Zuletzt hinzugefügt:** neue Lieder werden erkannt und separat gezeigt, damit du sie einsortieren kannst.

## Bedienung

Überall dieselbe Logik, in jeder Liedliste:

- Nach rechts wischen = Lieblingssong, nach links wischen = Warteschlange.
- Lange drücken = Mehrfachauswahl.
- Schnellsymbole in der Zeile: Wiedergaben, Herz, Zu Playlist, Sperren, dazu ein Menü.
- Tippen auf den Interpreten öffnet ihn, die Zurück-Taste führt dorthin zurück, wo du warst.
- Tippen auf eine Meldung wie „Zu Lieblingssongs hinzugefügt“ öffnet das Ziel.
- Löschen und Entfernen sind farblich getrennt (Rot für Löschen, Orange für Änderungen an Dateien). In Playlists und Hörbüchern heißt die Aktion „Aus Playlist entfernen“.
- Listengröße Small, Medium und Large mit echter Dichte.

## Was die App intern macht

### Namen bereinigen (nur Anzeige und Sortierung)

- Führende Tracknummern werden entfernt (`01 - Song`, `1. Song`, `01_Song`, `(01) Song`).
- Namen wie `2Pac`, `50 Cent`, `10cc`, `3 Doors Down` und `1979` bleiben unangetastet. Bei Dateien ohne Trennzeichen wie `1 Song` greift die Regel nur, wenn die Mehrheit der Dateien im Ordner so aufgebaut ist.
- Unterstriche werden zu Leerzeichen, Dateiendungen im Namen (`Song.mp3.mp3`) verschwinden.
- Steht der Interpret schon im Titel (`Ice Cube - Titel`), wird er herausgenommen. Das funktioniert auch bei Schreibvarianten wie `AC/DC`, `AC-DC` und `ACDC`.
- Doppelte Titelteile (`Titel - Titel`) werden zusammengefasst, eigene Wörter wie „(Official Video)“ kannst du in den Einstellungen als Filter pflegen.

### Dateien umbenennen und sortieren (nur auf deinen Wunsch)

- **Datei bearbeiten:** Name und Interpret ändern, Dateinamen aus den bereinigten Namen übernehmen. Der orange Speichern-Knopf im Player macht das mit einem Tipp.
- **Interpret zuordnen:** für einzelne Lieder oder viele auf einmal. Optional werden die Dateien in `<Speicher>/Music/<Interpret>` verschoben. Eine SD-Karte bleibt dabei eine SD-Karte.
- **Schutz vor Chaos:** Lieder, die schon in einem Interpreten- oder Album-Ordner liegen, werden nie herausgezogen. Du kannst einstellen, ob Schnell-Speichern verschieben soll.
- **Pfade nachziehen:** Nach jedem Verschieben oder Umbenennen werden Playlists, Favoriten, Sperren, Wiedergabezähler und Zuordnungen auf den neuen Pfad umgestellt. Nichts geht verloren.
- **Datei zeigen:** öffnet den Ordner im Dateimanager.
- Verändert wird nichts ohne Bestätigung. Löschen von Dateien gibt es immer mit Rückfrage und ohne Papierkorb.

### Interpreten erkennen und zuordnen

- Interpret kommt aus dem Tag, sonst aus dem Ordnernamen. Ordner ohne Interpret prüfst du in den Einstellungen.
- Ähnliche Schreibweisen (Tippfehler, `X feat. Y`, andere Schreibung) werden vorgeschlagen, damit ein Interpret nicht doppelt auftaucht.

### Stille überspringen (internes „Schnittprogramm“)

- Die App liest jedes Lied einmal durch und sucht den ersten und den letzten Ton, der lauter als eine einstellbare Schwelle ist.
- Gespielt wird nur von „erster Ton minus Vorlauf“ bis „letzter Ton plus Nachlauf“. Der Hall bleibt dabei stehen. Pausen mitten im Lied bleiben unberührt.
- Die Datei selbst wird nie verändert. Das Ergebnis wird pro Lied gespeichert und gilt nur für die aktuelle Schwelle.
- Beim ersten Abspielen wird zuerst nur der Anfang untersucht, damit das Lied sofort startet. Die vollständige Analyse läuft im Hintergrund nach.
- Schwelle, Vorlauf und Nachlauf stellst du in den Einstellungen ein.

### Duplikate

- Verglichen werden ähnlicher Name, ähnliche Dateigröße und gleiche Länge, nur über Metadaten.
- Die Seite hilft mit „Rest markieren“ und Löschen mit Bestätigung, außerdem mit Dateinamen übernehmen und Aufräumen.

### Cover

- Lieder, Interpreten, Playlists und Alben haben Cover. Fehlt eines, wird automatisch ein Bild aus den enthaltenen Liedern zusammengesetzt. Eigene Bilder gehen vor.
- **Cover-Suche:** sucht live über iTunes und Deezer, mit dem aktuellen Namen und Interpreten. Das ist der einzige Teil der App, der das Internet nutzt.
- Cover lassen sich einzeln und für mehrere Lieder gleichzeitig ändern.
- Ein interner Cover-Dienst reicht die Bilder auch an Android Auto weiter.

### Speicher

- Alles, was die App selbst speichert (Playlists, Favoriten, Sperren, Zuordnungen, Einstellungen, Schnitt-Ergebnisse), liegt in einer JSON-Datei in den App-Daten. Deine Musikdateien bleiben unberührt, solange du nichts ausdrücklich änderst.
- Playlist-Einträge sind Pfade, keine Kopien. Beim Hinzufügen bereits vorhandener Lieder kommt eine Warnung mit „Doppelte überspringen“.

## APK über GitHub bauen

1. Neues GitHub-Repository anlegen und den Inhalt dieses Ordners hochladen, auch den versteckten Ordner `.github` und die Datei `debug.keystore`.
2. Unter **Actions** startet der Bau automatisch (ca. 3–5 Minuten).
3. Fertige APK: **Releases → „Musikplayer (neueste Version)“ → Musikplayer.apk**, oder unter Actions → Lauf → Artifacts.
4. APK auf dem Handy öffnen, „Installation aus unbekannten Quellen“ erlauben, installieren.
5. Beim ersten Start „Zugriff auf alle Dateien“ erlauben.

Updates: Code ändern, hochladen, neue APK laden und drüberinstallieren (gleiche Signatur).

## Android Auto

Die App meldet sich als Medien-App. Weil sie nicht aus dem Play Store kommt, muss in der Android-Auto-App auf dem Handy die Entwicklereinstellung **„Unbekannte Quellen“** aktiviert sein: Android Auto → Einstellungen → Version mehrfach antippen → Entwicklereinstellungen → Unbekannte Quellen.

## Für Entwickler und KI-Helfer

Die verbindlichen Arbeitsregeln stehen in [`AI_RULES.md`](AI_RULES.md). Erledigte und anstehende Updates stehen in [`ROADMAP.md`](ROADMAP.md). Kurzfassung: Änderungen gelten app-weit für vergleichbare Stellen, Funktionen und Navigation sollen einheitlich sein, und Build-Status wird ehrlich gemeldet.
