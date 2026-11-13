# Mitgelieferte Bibliotheken

Das Release-Archiv enthält neben `rechnungsgenerator-<version>.jar` 49 fremde
JAR-Dateien. Diese Datei nennt sie mit ihrer Lizenz – Apache-2.0 §4,
BSD-3-Clause, MIT und MPL-2.0 §3.3 verlangen, dass die Vermerke mit der
Binärfassung weitergegeben werden, und nur 25 der 49 tragen selbst einen.

Keine dieser Bibliotheken ist verändert. Wo eine Lizenz den Quelltext
verlangt, genügt deshalb der Verweis auf den Upstream.

Geschrieben und gepflegt wird die Liste von Hand. Ein Generator liest die
POM-Dateien, und dort steht bei den Paketen, auf die es ankommt, nichts: Die
sechs veraPDF-Artefakte erben ihre Lizenz vom Eltern-POM, das die Wahl
zwischen zwei Fassungen offenlässt, und die JAXB-Pakete von Eclipse schweigen
im POM fast alle, während die mitgelieferte `LICENSE.md` ein BSD-3-Clause-Text
ist. Dass die Liste
vollständig bleibt, hält `ThirdPartyNoticesTest` fest: Kommt ein JAR dazu oder
fällt eines weg, wird er rot.

## Apache License 2.0

<https://www.apache.org/licenses/LICENSE-2.0>

- `annotations-13.0.jar` – JetBrains Java Annotations
- `clikt-jvm.jar` – Clikt
- `commons-io-2.22.0.jar` – Apache Commons IO
- `commons-logging-1.4.0.jar` – Apache Commons Logging
- `fontbox-3.0.8.jar` – Apache PDFBox FontBox
- `jackson-annotations-2.22.jar` – Jackson
- `jackson-core-2.22.3.jar` – Jackson
- `jackson-databind-2.22.3.jar` – Jackson
- `jsr305-3.0.2.jar` – JSR 305 Annotations
- `kaml-jvm-0.104.0.jar` – kaml
- `kotlin-stdlib-2.4.21.jar` – Kotlin Standard Library
- `kotlinx-serialization-core-jvm-1.9.0.jar` – kotlinx.serialization
- `library-2.26.0.jar` – Mustang, <https://www.mustangproject.org/>
- `okio-jvm-3.16.4.jar` – Okio
- `pdfbox-3.0.8.jar` – Apache PDFBox
- `pdfbox-io-3.0.8.jar` – Apache PDFBox IO
- `ph-commons-11.2.0.jar` – ph-commons
- `ph-jaxb-11.2.0.jar` – ph-commons (JAXB)
- `ph-schematron-api-8.0.6.jar` – ph-schematron
- `ph-schematron-xslt-8.0.6.jar` – ph-schematron (XSLT)
- `ph-xml-11.2.0.jar` – ph-commons (XML)
- `ph-xsds-xml-3.0.0.jar` – ph-xsds
- `preflight-3.0.8.jar` – Apache PDFBox Preflight
- `snakeyaml-engine-kmp-jvm-4.0.1.jar` – snakeyaml-engine-kmp
- `urlencoder-lib-jvm-1.6.0.jar` – UrlEncoder
- `validator-2.26.0.jar` – Mustang Validator, <https://www.mustangproject.org/>
- `xmpbox-3.0.8.jar` – Apache PDFBox XmpBox
- `xmlresolver-5.3.3.jar` – XML Resolver
- `xmlresolver-5.3.3-data.jar` – XML Resolver (Daten)

## Mozilla Public License 2.0

<https://www.mozilla.org/MPL/2.0/>

Das Copyleft der MPL bindet je Datei. Keine dieser Dateien ist hier geändert;
der Quelltext ist beim jeweiligen Projekt zu beziehen.

- `Saxon-HE-12.10.jar` – Saxon-HE, <https://github.com/Saxonica/Saxon-HE>
- `rhino-1.7.15.1.jar` – Mozilla Rhino, <https://github.com/mozilla/rhino>

## BSD 3-Clause License

<https://opensource.org/licenses/BSD-3-Clause>

- `verapdf-xmp-core-jakarta-1.30.4.jar` – veraPDF XMP Core, abgeleitet von
  Adobe XMP Core
- `stax-utils-20070216.jar` – stax-utils
- `dom4j-2.2.0.jar` – dom4j

Die folgenden stehen unter der Eclipse Distribution License 1.0
(<https://www.eclipse.org/org/documents/edl-v10.php>). Ihr Text ist die
BSD-3-Clause-Lizenz mit der Eclipse Foundation als Namensgeber (Copyright (c)
2018 Oracle and/or its affiliates). Wo das JAR ihn mitbringt, liegt er als
`META-INF/LICENSE.md` bei; `istack-commons-runtime` trägt keinen und erbt die
Lizenz vom Eltern-POM `istack-commons`.

- `angus-activation-2.0.0.jar` – Eclipse Angus Activation
- `istack-commons-runtime-4.1.1.jar` – istack common utility code
- `jakarta.activation-api-2.1.4.jar` – Jakarta Activation (API)
- `jakarta.xml.bind-api-4.0.5.jar` – Jakarta XML Binding (API)
- `jaxb-core-4.0.2.jar` – JAXB Core
- `jaxb-runtime-4.0.2.jar` – JAXB Runtime
- `txw2-4.0.2.jar` – TXW2 Runtime

## MIT License

<https://opensource.org/licenses/MIT>

- `slf4j-api-2.0.20.jar` – SLF4J API
- `slf4j-nop-2.0.20.jar` – SLF4J NOP Provider

Copyright (c) 2004-2023 QOS.ch; der Lizenztext liegt beiden JARs als
`META-INF/LICENSE.txt` bei.

## GPL v3 oder höher **oder** MPL 2.0 oder höher

Doppelt lizenziert; für dieses Programm ist **MPL-2.0** gewählt. GPLv3 schlüge
auf das Ganze durch (ADR-0014).

Quelltext und Lizenzangaben: <https://verapdf.org/> und
<https://github.com/veraPDF>

- `core-jakarta-1.30.4.jar` – veraPDF Core
- `feature-reporting-jakarta-1.30.4.jar` – veraPDF Feature Reporting
- `metadata-fixer-jakarta-1.30.4.jar` – veraPDF Metadata Fixer
- `parser-1.30.4.jar` – veraPDF Parser
- `pdf-model-1.30.4.jar` – veraPDF PDF Model
- `validation-model-jakarta-1.30.4.jar` – veraPDF Validation Model
