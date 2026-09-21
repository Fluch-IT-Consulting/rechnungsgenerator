# Mitgelieferte Bibliotheken

Das Release-Archiv enthält neben `rechnungsgenerator-<version>.jar` 27 fremde
JAR-Dateien. Diese Datei nennt sie mit ihrer Lizenz – Apache-2.0 §4,
BSD-3-Clause und MPL-2.0 §3.3 verlangen, dass die Vermerke mit der
Binärfassung weitergegeben werden, und nur 7 der 27 tragen selbst einen.

Keine dieser Bibliotheken ist verändert. Wo eine Lizenz den Quelltext
verlangt, genügt deshalb der Verweis auf den Upstream.

Geschrieben und gepflegt wird die Liste von Hand. Ein Generator liest die
POM-Dateien, und dort steht bei den Paketen, auf die es ankommt, nichts: Die
sechs veraPDF-Artefakte erben ihre Lizenz vom Eltern-POM, das die Wahl
zwischen zwei Fassungen offenlässt, und `jaxb-impl` schweigt im POM, während
die mitgelieferte `LICENSE.md` ein BSD-3-Clause-Text ist. Dass die Liste
vollständig bleibt, hält `ThirdPartyNoticesTest` fest: Kommt ein JAR dazu oder
fällt eines weg, wird er rot.

## Apache License 2.0

<https://www.apache.org/licenses/LICENSE-2.0>

- `annotations-13.0.jar` – JetBrains Java Annotations
- `clikt-jvm.jar` – Clikt
- `jackson-annotations-2.22.jar` – Jackson
- `jackson-core-2.22.1.jar` – Jackson
- `jackson-databind-2.22.1.jar` – Jackson
- `kaml-jvm-0.104.0.jar` – kaml
- `kotlin-stdlib-2.4.20.jar` – Kotlin Standard Library
- `kotlinx-serialization-core-jvm-1.9.0.jar` – kotlinx.serialization
- `okio-jvm-3.16.4.jar` – Okio
- `snakeyaml-engine-kmp-jvm-4.0.1.jar` – snakeyaml-engine-kmp
- `urlencoder-lib-jvm-1.6.0.jar` – UrlEncoder
- `xmlresolver-5.3.3.jar` – XML Resolver
- `xmlresolver-5.3.3-data.jar` – XML Resolver (Daten)

## Mozilla Public License 2.0

<https://www.mozilla.org/MPL/2.0/>

Das Copyleft der MPL bindet je Datei. Keine dieser Dateien ist hier geändert;
der Quelltext ist beim jeweiligen Projekt zu beziehen.

- `Saxon-HE-12.8.jar` – Saxon-HE, <https://github.com/Saxonica/Saxon-HE>
- `rhino-1.7.15.1.jar` – Mozilla Rhino, <https://github.com/mozilla/rhino>

## BSD 3-Clause License

<https://opensource.org/licenses/BSD-3-Clause>

- `verapdf-xmp-core-1.30.2.jar` – veraPDF XMP Core, abgeleitet von Adobe XMP Core
- `stax-utils-20070216.jar` – stax-utils
- `jaxb-impl-2.3.2.jar` – JAXB Runtime (Copyright (c) 2018 Oracle and/or its
  affiliates; der Lizenztext liegt dem JAR als `META-INF/LICENSE.md` bei)

## CDDL 1.1 mit GPL 2.0 und Classpath Exception

<https://oss.oracle.com/licenses/CDDL+GPL-1.1>

Doppelt lizenziert. Die Classpath Exception ist der Grund, warum das Einbinden
unbedenklich ist: Sie nimmt das Verlinken ausdrücklich vom Copyleft aus.

- `jaxb-api-2.4.0-b180830.0359.jar` – Java Architecture for XML Binding (API)
- `jaxb-core-2.3.0.1.jar` – JAXB Core
- `javax.activation-api-1.2.0.jar` – JavaBeans Activation Framework (API)

## GPL v3 oder höher **oder** MPL 2.0 oder höher

Doppelt lizenziert; für dieses Programm ist **MPL-2.0** gewählt. GPLv3 schlüge
auf das Ganze durch (ADR-0014).

Quelltext und Lizenzangaben: <https://verapdf.org/> und
<https://github.com/veraPDF>

- `core-1.30.2.jar` – veraPDF Core
- `feature-reporting-1.30.2.jar` – veraPDF Feature Reporting
- `metadata-fixer-1.30.2.jar` – veraPDF Metadata Fixer
- `parser-1.30.2.jar` – veraPDF Parser
- `pdf-model-1.30.2.jar` – veraPDF PDF Model
- `validation-model-1.30.2.jar` – veraPDF Validation Model
