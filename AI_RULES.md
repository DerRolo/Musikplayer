# AI_RULES.md – Verbindliche Arbeitsregeln für den Musikplayer

Diese Datei ist bei jeder KI-gestützten Arbeit am Musikplayer zu lesen und als verbindliche Leitlinie zu behandeln. Sie soll verhindern, dass Anforderungen wiederholt erklärt werden müssen, Änderungen nur lokal umgesetzt werden oder sich die App uneinheitlich entwickelt.

## 1. Grundprinzip: Einheitlichkeit ist der Standard

- **Eine Änderung gilt grundsätzlich app-weit für alle vergleichbaren Stellen.**
- Wenn der Nutzer ein Verhalten, ein Symbol, ein Layout, eine Bedienmöglichkeit oder eine Regel an einer Stelle beschreibt, prüfe alle anderen Ansichten und Komponenten, in denen dasselbe Element oder dieselbe Funktion vorkommt.
- Setze eine Änderung nicht nur an der genannten Stelle um, wenn sie sinnvollerweise eine gemeinsame Regel oder ein gemeinsames UI-Muster betrifft.
- Übertrage Verbesserungen auf alle vergleichbaren Bereiche, ohne dass der Nutzer jede einzelne Stelle aufzählen muss.
- Einheitlichkeit bedeutet: gleiche Funktion, gleiche Bedeutung, gleiche Bedienlogik und möglichst gleiche visuelle Darstellung. Unterschiede sind nur gerechtfertigt, wenn sie aufgrund des jeweiligen Kontextes sinnvoll oder technisch notwendig sind.
- Wenn eine Änderung bewusst nur für eine bestimmte Ansicht gelten soll, muss der Nutzer das ausdrücklich sagen – oder es muss aus dem Kontext eindeutig hervorgehen.

### Beispiel
Wenn der Nutzer in einer Titelliste sagt, dass ein Titel ein Herz zum Favorisieren und ein Plus zum Hinzufügen zu einer Playlist haben soll, ist zu prüfen und umzusetzen, dass diese Funktionen auch in allen vergleichbaren Titellisten verfügbar sind, z. B. in Bibliothek, Suche, Album-, Künstler- oder anderen Titelansichten. Nicht einfach nur die zuerst genannte Liste ändern.

## 2. Funktionsgleichheit für vergleichbare Inhalte

Vergleichbare Inhaltstypen sollen vergleichbare Funktionen und Bedienmöglichkeiten besitzen.

- **Playlists und Hörbücher:** Wenn der Nutzer Playlists bearbeitbar machen möchte, prüfe automatisch, ob die entsprechenden Bearbeitungsmöglichkeiten auch für Hörbücher sinnvoll und vorhanden sein müssen. Hörbücher nicht ohne nachvollziehbaren Grund als Sonderfall mit weniger Funktionen behandeln.
- **Titel:** Aktionen wie Favorisieren, zu einer Playlist hinzufügen, Auswahl, Sortierung oder weitere vorhandene Titel-Aktionen sollen in vergleichbaren Titelansichten konsistent sein.
- **Alben, Künstler, Ordner und andere Bibliotheksobjekte:** Gemeinsame Aktionen und Darstellungsmuster über alle vergleichbaren Ansichten hinweg berücksichtigen.
- Unterschiedliche Inhaltstypen dürfen spezielle Funktionen haben, wenn diese inhaltlich sinnvoll sind (z. B. Hörbuch-spezifische Funktionen). Das rechtfertigt aber nicht, gemeinsame Funktionen wegzulassen.


## 2.1 Funktionsparität, Optionsmenüs und kontextgerechte Aktionen

Nicht nur das Design, sondern auch **Funktionsumfang und Optionsmenüs** sollen für gleiche Objekte und vergleichbare Ansichten konsistent sein.

- Wenn ein Objekt (z. B. ein Lied) in mehreren Ansichten verfügbar ist, prüfe, ob die Aktionen für dieses Objekt in diesen Ansichten grundsätzlich vollständig und konsistent angeboten werden.
- Ein Aktionsmenü (z. B. Drei-Punkte-Menü) soll für dasselbe Objekt nicht ohne nachvollziehbaren Grund in einer Ansicht umfangreich und in einer anderen stark eingeschränkt sein.
- Beispiel: Lied-Aktionen wie Datei bearbeiten, umbenennen, verschieben, sperren oder andere bereits vorhandene Verwaltungsaktionen sollen in allen geeigneten Lied-Kontexten berücksichtigt werden (Bibliothek, Künstler-/Albumansicht, Playlist, Suche usw.). Prüfe dabei, welche Aktionen in dem jeweiligen Kontext tatsächlich ausführbar sind.
- Fehlende Aktionen nicht einfach als kontextbedingt akzeptieren oder weglassen: erst prüfen, ob es einen echten Grund gibt. Wenn die Aktion im Kontext sinnvoll und technisch möglich ist, soll sie dort verfügbar sein.
- **Kontextgerechte Konsistenz statt blindes Kopieren:** Nicht jede Funktion, Geste oder Aktion muss überall gleich übernommen werden. Eine Aktion kann in einem Kontext sinnvoll sein und in einem anderen unsinnig, verwirrend oder gefährlich sein.
- Beispiele für nicht blind zu übertragende Funktionen: Swipe-Gesten, Schnellaktionen, Sortier- und Filteroptionen, kontextabhängige Aktionen oder Funktionen, die nur für einen bestimmten Inhaltstyp Sinn ergeben. Eine Geste zum Hinzufügen zu Favoriten ist nicht automatisch für eine Sperrliste oder einen anderen Bereich passend.
- Bei Optionen/Einstellungen (Settings) ebenfalls auf konsistente Struktur, Benennung, Gruppierung, Bedienung, Rückmeldungen, Schalter/Dropdowns und Vorschauen achten. Ähnliche Optionen sollen möglichst gleich aufgebaut sein. Unterschiedliche Optionen dürfen unterschiedliche Steuerelemente haben, wenn das fachlich und UX-seitig sinnvoll ist.
- Wenn nicht klar ist, ob eine Aktion oder Option in einem anderen Kontext übernommen werden sollte, **gezielt nachfragen**. Nicht einfach übernehmen, nicht stillschweigend weglassen und nicht nach eigenem Gutdünken eine Sonderregel erfinden.
- Bei Änderungen an Aktionsmenüs und Optionen stets eine **Kontextprüfung** durchführen: In welchen Ansichten ist das Objekt verfügbar? Welche Aktionen gibt es bereits? Wo fehlen sie? Wo wären sie unpassend? Was soll überall gleich sein, was bleibt kontextbezogen?

### Beispiel: Drei-Punkte-Menü bei Liedern
Wenn das Drei-Punkte-Menü in der Bibliothek Verwaltungsaktionen für ein Lied anbietet, prüfe die Menüparität für dasselbe Lied auch in Künstler-/Albumansichten, Playlists, Suche und weiteren Liedlisten. Ziel ist ein konsistenter Satz sinnvoller Aktionen. Nicht relevante Aktionen dürfen kontextbedingt entfallen, aber die Begründung dafür muss nachvollziehbar sein; bei Zweifeln Rückfrage.

### Beispiel: Optionen
Wenn eine Einstellungsseite eine bestimmte Gestaltung oder Bedienlogik verwendet (z. B. Gruppierung, Vorschau, Beschreibung, Schalter), orientiere ähnliche Einstellungsseiten daran. Übertrage nicht die konkrete Funktion einer Einstellung auf andere Einstellungen, sondern die passenden UI-/UX-Prinzipien.

## 3. Vor Umsetzung den Umfang einer Änderung bestimmen

Bei jeder Anforderung zuerst klären, ob sie sich auf Folgendes auswirkt:

1. Nur ein einzelnes Element.
2. Eine einzelne Ansicht.
3. Alle Ansichten mit diesem Element oder Inhaltstyp.
4. Mehrere Inhaltstypen mit gleicher oder ähnlicher Funktion.
5. Globale App-Regeln, Einstellungen, Navigation oder Datenverhalten.

**Nicht automatisch den kleinstmöglichen Umfang wählen.** Der sinnvolle Umfang ergibt sich aus der Bedeutung der Anforderung und den bestehenden Mustern der App.

## 4. Rückfragen bei Mehrdeutigkeit

Der Nutzer möchte nicht jede Folgeänderung einzeln aufzählen müssen. Die KI soll eigenständig Zusammenhänge erkennen. Trotzdem gilt:

- Wenn mehrere plausible Umsetzungen existieren und die Entscheidung die Bedienung, Navigation, Daten, Darstellung oder den Funktionsumfang wesentlich verändert, **vorher eine konkrete Rückfrage stellen**.
- Rückfragen sollen gezielt sein und möglichst nur die tatsächlich offene Entscheidung betreffen.
- Nicht für offensichtliche Folgeänderungen nachfragen, die sich aus Einheitlichkeit und der Nutzeranforderung ergeben.
- Keine unklaren Anforderungen eigenmächtig auf eine einzelne Ansicht begrenzen.
- Wenn eine Änderung eine bestehende Regel oder frühere Nutzerentscheidung berührt, diese berücksichtigen und bei einem echten Konflikt nachfragen.

## 5. Einheitliches UI und Design

- Bestehende Design- und Bedienmuster als gemeinsame Standards betrachten.
- Symbole, Abstände, Größen, Typografie, Farben, Interaktionsflächen, Menüs, Dialoge und Zustände möglichst konsistent halten.
- Bei UI-Änderungen auch kleine, große und alternative Darstellungsmodi prüfen, sofern die betreffende Komponente diese unterstützt.
- Einstellungen und Optionen sollen nicht nur in der eigentlichen Ansicht funktionieren, sondern auch in Vorschauen, alternativen Layouts und allen relevanten Zuständen korrekt berücksichtigt werden.
- Gemeinsame UI-Komponenten bevorzugen, statt dieselbe Funktion mehrfach unabhängig und leicht unterschiedlich zu implementieren.
- Bestehende Komponenten oder Regeln erweitern, wenn das sinnvoll ist; keine unnötigen Duplikate schaffen.

## 6. Navigation und Kontext

Navigation ist ein app-weites System und darf nicht isoliert pro Bildschirm behandelt werden.

- Navigationsverhalten, Zurück-Aktion und Wiederherstellung des vorherigen Kontextes sollen über die App hinweg konsistent sein.
- Beim Öffnen eines Unterbildschirms muss nachvollziehbar sein, wohin die Zurück-Aktion führt.
- Wenn ein Bildschirm aus unterschiedlichen Kontexten geöffnet werden kann, muss der Rückweg den passenden Ursprung berücksichtigen, statt pauschal an eine unpassende Stelle zu springen.
- Zurück zum vorherigen Bildschirm soll möglichst den vorherigen Zustand bewahren (z. B. Suchbegriff, Scrollposition, Auswahl, geöffneter Kontext), soweit dies mit der vorhandenen Architektur sinnvoll und zuverlässig umsetzbar ist.
- Navigation nicht nur für den gerade gemeldeten Weg korrigieren: verwandte Navigationswege und vergleichbare Screens mit prüfen.
- Keine neue Navigation erfinden oder bestehende Navigationsregeln verändern, wenn die gewünschte Zielstruktur nicht klar ist. In diesem Fall nachfragen.
- Navigation nicht durch ad-hoc-Sonderfälle verkomplizieren, wenn eine einheitliche Lösung möglich ist.

## 7. Daten, Speicherung und Verhalten

- Nutzerbezogene Änderungen, Einstellungen, Auswahlzustände und relevante Daten müssen dauerhaft gespeichert werden, sofern sie nach App-Neustart erhalten bleiben sollen.
- Vor Änderungen an Datenlogik, automatischer Zuordnung, Verschieben, Umbenennen, Album-/Ordnererkennung oder anderen automatischen Vorgängen besonders vorsichtig sein.
- Automatische Erkennung oder Zuordnung nicht ohne ausdrücklichen Auftrag verändern. Bei Unsicherheit Rückfrage stellen.
- Keine bestehenden Daten, Funktionen, Einstellungen oder Abläufe unbeabsichtigt entfernen, zurücksetzen oder überschreiben.
- Neue Funktionen sollen mit bestehenden Funktionen zusammenspielen und nicht deren Verhalten ungewollt verändern.
- Zustände müssen bei allen relevanten Ansichten konsistent sein. Beispielsweise darf eine Änderung an einem Objekt nicht nur in einer Ansicht sichtbar sein, wenn dasselbe Objekt an anderen Stellen angezeigt wird.

## 8. Änderungen sorgfältig und möglichst zentral implementieren

- Zuerst vorhandenen Code, Architektur, Komponenten, Datenfluss und ähnliche Funktionen untersuchen.
- Wenn eine gemeinsame Komponente, ein gemeinsamer State oder eine zentrale Logik existiert, diese bevorzugt nutzen.
- Änderungen möglichst gezielt und nachvollziehbar halten.
- Keine unnötigen Refactorings, Umbenennungen, Formatierungsänderungen oder sonstigen Änderungen neben der eigentlichen Aufgabe.
- Keine bestehenden Funktionen entfernen, nur weil sie beim aktuellen Auftrag nicht gebraucht werden.
- Keine bloßen visuellen Workarounds verwenden, wenn eine korrekte gemeinsame Lösung möglich ist.

## 9. Arbeitsablauf bei jedem Änderungsauftrag

Für jede Aufgabe gilt diese Reihenfolge:

1. **Anforderung verstehen:** Was ist das eigentliche gewünschte Verhalten, nicht nur die wörtlich genannte Einzelstelle?
2. **Kontext prüfen:** Bestehende Implementierung, verwandte Screens, gemeinsame Komponenten und bereits geltende Regeln untersuchen.
3. **Auswirkungsbereich bestimmen:** Alle vergleichbaren Stellen und Folgeeffekte identifizieren.
4. **Unklarheiten klären:** Bei relevanten offenen Entscheidungen gezielt Rückfragen stellen.
5. **Umsetzen:** Konsistent und möglichst über gemeinsame Komponenten oder Logik.
6. **Folgestellen prüfen:** Sicherstellen, dass vergleichbare Ansichten, Inhaltstypen, Einstellungen, Vorschauen und Navigation ebenfalls berücksichtigt sind.
7. **Validieren:** Build bzw. verfügbare Prüfungen ausführen; Fehler nicht als behoben oder erfolgreich getestet darstellen, wenn sie nicht tatsächlich geprüft wurden.
8. **Berichten:** Kurz erklären, was geändert wurde, welche weiteren Bereiche mit angepasst wurden, welche Prüfungen erfolgreich waren und welche Einschränkungen verbleiben.

## 10. Checkliste vor Abschluss

Vor Abschluss einer Änderung diese Punkte prüfen:

- [ ] Ist die eigentliche Anforderung umgesetzt?
- [ ] Wurde die Änderung auch an allen vergleichbaren Stellen umgesetzt?
- [ ] Wurden ähnliche Inhaltstypen (z. B. Playlist und Hörbuch) auf Funktionsgleichheit geprüft?
- [ ] Sind alle relevanten Layoutvarianten und Einstellungen berücksichtigt?
- [ ] Ist die Navigation inklusive Zurückweg und Ursprungskontext geprüft?
- [ ] Werden Daten und Einstellungen korrekt gespeichert und bestehende Daten geschützt?
- [ ] Wurden bestehende Funktionen nicht unbeabsichtigt verändert oder entfernt?
- [ ] Wurde ein Build oder eine andere geeignete Prüfung ausgeführt?
- [ ] Sind nicht getestete Punkte transparent genannt?

## 11. Kommunikation und Lieferumfang

- Kompakt, sachlich und ehrlich kommunizieren.
- Keine Erfolgsmeldung für einen Build, Test oder eine Funktion geben, die nicht tatsächlich geprüft wurde.
- Wenn die Umgebung keinen vollständigen Android-Build ermöglicht, klar sagen, was stattdessen geprüft wurde und was noch offen ist.
- Wenn ein Patch gewünscht ist, grundsätzlich nur die geänderten bzw. benötigten Dateien bereitstellen, mit korrekten Projektpfaden und ohne unnötige Dateien.
- Bei einem Patch darauf achten, dass er auf dem richtigen Projektstand basiert. Keine alten oder bereits überholten Patch-Dateien weiterverwenden.
- Bei größeren Änderungen die betroffenen Bereiche kurz auflisten, damit der Nutzer nachvollziehen kann, ob der gesamte erwartete Umfang erfasst wurde.

## 12. Leitgedanke

**Der Nutzer beschreibt oft eine konkrete Stelle, aber meint damit eine allgemeine Regel.** Die KI soll diese Regel erkennen, auf die gesamte App übertragen und die Konsistenz der App aktiv sicherstellen.

Wenn die Reichweite nicht sicher erkennbar ist oder unterschiedliche sinnvolle Interpretationen existieren, lieber eine gezielte Rückfrage stellen als eine unvollständige oder uneinheitliche Lösung liefern.
