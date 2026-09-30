# Die Werkzeuge werden nach Art geschnitten

Hebt für die Werkzeuge den Schnitt nach Verbraucher aus ADR-0023 auf.

Ein Werkzeug liegt in dem Paket seiner Art, nicht in dem Paket des Kommandos,
das es braucht:

| Paket | Art | Inhalt |
|---|---|---|
| `toolchain` | Basis | `ToolchainException`, `XmlParser` |
| `toolchain.process` | Adapter auf ein Programm | `ProcessRunner`, `ExternalTool`, `ToolResult`, `VeraPdf`, `LuaLatex` |
| `toolchain.validation` | Prüfer | `PdfAValidator` samt Adaptern und Ergebnis, `EInvoiceValidator` samt Ergebnis |
| `generate.typesetting` | Satz und Einbetten | `Typesetter` samt `LuaLatexTypesetter`, `PdfSources`, `LatexCompiler`, `EInvoiceEmbedder` |

Die Kanten zeigen von `generate.typesetting` über `toolchain.validation` zu
`toolchain.process` und von allen dreien zur Basis. `xml` importiert aus
`toolchain.validation` und der Basis.

Ein Adapter auf ein Programm kennt dessen Protokoll: Name,
Installationshinweis, Parameter, die Bedeutung der Exitcodes und wo das
Ergebnis steht. Was das Ergebnis bedeutet, entscheidet der Aufrufer.
`VeraPdf` liefert Urteil und Bericht, `ExternalPdfAValidator` liest daraus die
Verstöße. `LuaLatex` liefert je Lauf, ob er scheiterte, und das Protokoll;
`LatexCompiler` zählt die Läufe, erkennt den Überlauf (ADR-0020) und baut die
Meldung.

## Warum

**Gleichartiges lag getrennt.** `LatexCompiler` und `ExternalPdfAValidator`
sind beide Adapter auf ein Programm über `ProcessRunner`, `EInvoiceValidator`
und `EInvoiceEmbedder` sprechen beide Mustang an. Je zwei lagen in zwei
Paketen, weil sie verschiedene Kommandos brauchen.

**Nach Verbraucher zieht eine Klasse um, sobald ein zweites Kommando sie
braucht.** So zogen die Prüfer mit `xml` aus `generate.toolchain` in den Rand
(ADR-0023, Nachtrag vom 26.09.2026). Die Art einer Klasse ändert sich nicht,
wenn ein Kommando dazukommt.

**Aufruf und Deutung waren vermischt.** Beide Adapter bauten die Parameter,
lasen die Exitcodes und deuteten das Ergebnis in einer Klasse. Getrennt prüft
ein Test das Protokoll ohne die Deutung und die Deutung mit einem
vorbereiteten Lauf.

**Satz und Einbetten bleiben beim Verbraucher.** `LatexCompiler` kennt
`PdfSources`, und `EInvoiceEmbedder` gehört zum Weg vom Quelltext zum PDF
hinter der Naht `Typesetter`. Für `xml` sichtbar wird nur das dünne Protokoll
`LuaLatex`, nicht der Satz. Was ADR-0023 unter „Das ganze Paket
`generate.toolchain` als Rand“ verworfen hat, bleibt damit verworfen.

## Verworfen

- **Beim Schnitt nach Verbraucher bleiben.** Siehe oben; zweimal „toolchain“
  las sich zudem wie ein Paket, das man in zwei Teile geschnitten hat.
- **`EInvoiceEmbedder` in `generate.einvoice`.** `einvoice` liest die
  `AcceptedInvoice`; das wäre ein Schnitt nach Thema statt nach Art, und der
  Satz hinge über `einvoice` am Kern.
- **`VeraPdf` bekommt das `PdfAProfile`.** `toolchain.process` importiert
  nicht aus `toolchain.validation`; das Profil kommt als `cliFlag`.

## Konsequenzen

- **`PackageStructureTest`** bekommt Zeilen für `toolchain.process` und
  `toolchain.validation`, `generate.toolchain` heißt `generate.typesetting`.
- **Das Test-Tag `Toolchain` und `ToolchainIntegrationTest` behalten ihren
  Namen.** Sie bedeuten „braucht die Werkzeuge“, nicht das Paket.
- **Nicht Teil dieses Schnitts:** eine gemeinsame Form für Adapter auf ein
  Programm und `LatexCompiler(LuaLatex(ExternalTool))` hereinzureichen
  (`docs/notizen/2026-09-14-umbauten-vor-dem-wachsen.md`, Punkt 2).

Entschieden im Architektur-Review vom 30.09.2026, umgesetzt in #477.
