# zeus-easy-upload

Spring-Boot-Webanwendung zum Import beliebiger CSV-Dateien in IBM i DB2/400 Tabellen.

Version 1 Fokus:
- CSV hochladen und analysieren
- Spaltentypen automatisch vorschlagen
- Tabelle automatisch erzeugen
- Batch-INSERT in DB2/400
- GUI mit Thymeleaf (Bootstrap 5)

## Architektur

- `controller`: Web-Flow (`/`, `/upload`, `/import`)
- `service`: CSV-Parsing, Typinferenz, DDL und Import
- `domain`: Request/Result/Fehlermodelle
- `util`: Identifier- und Spaltensanitizing für AS400-Regeln
- `config`: Konfigurationseigenschaften (`app.*`)

## Voraussetzungen

- Java 17
- Maven 3.9+
- Netzwerkzugriff auf IBM i
- Benutzer mit Rechten zum:
  - `CREATE TABLE`
  - `DROP TABLE` (wenn Option aktiviert)
  - `INSERT`

## Konfiguration

Datei: `src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    driver-class-name: com.ibm.as400.access.AS400JDBCDriver
    url: jdbc:as400://system/bib;translate binary=true
    username: user
    password: pass

  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

app:
  default-library: BIB
  sample-rows: 200
  batch-size: 500
```

## IBM i JDBC Hinweise

- Schema entspricht `Library`.
- SQL arbeitet mit `LIB.TABLE`.
- Identifier werden in Großbuchstaben normalisiert.
- Spaltennamen werden auf `A-Z0-9_` reduziert.
- Spaltenlänge wird auf 10 Zeichen begrenzt (Trunkierung + Hash-Suffix).
- Leere Strings werden als `NULL` gespeichert.

## Starten

```bash
mvn clean package
mvn spring-boot:run
```

Anwendung: `http://localhost:8080`

## Nutzung

1. CSV hochladen, Library und Tabellenname eingeben.
2. Vorschau prüfen und Typen/Parameter bei Bedarf anpassen.
3. Import starten.
4. Ergebnisseite zeigt DDL, Anzahl Zeilen und detaillierte Fehlerliste.

## Typinferenz (v1)

Erkannte Zieltypen:
- `INTEGER`
- `BIGINT`
- `DECIMAL(p,s)`
- `DATE`
- `TIMESTAMP`
- `VARCHAR(n)`

Regeln:
- Analyse über maximal `app.sample-rows` Zeilen (Default: 200)
- Datum: `yyyy-MM-dd`, `dd.MM.yyyy`, `dd/MM/yyyy`
- Timestamp: `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd'T'HH:mm:ss`
- `VARCHAR(n)`: Max-Länge + 10%, Minimum 32, Maximum 4000

## Beispiel-CSV

`src/main/resources/examples/sample.csv`

## Tests

Enthaltene Unit-Tests:
- `ColumnNameSanitizerTest`
- `TypeInferenceServiceTest`
- `DecimalDetectionTest`
- `DateParsingTest`

Ausführen:

```bash
mvn test
```

## Screenshots

Optional: Screenshots von `index`, `preview`, `result` können später ergänzt werden.

## Erweiterbarkeit (Future)

- Update/Upsert (Merge-Strategie)
- Mehrere Delimiter-Profile
- Persistente Job-Historie
- Fehlerexport als CSV/JSON

## Metadata endpoints

Fuer kommende V2-Mapping-Features stellt die Anwendung zusaetzliche, read-only Metadata-Endpunkte bereit:

- `/meta/tables?library=LIB`
- `/meta/columns?library=LIB&table=TABLE`
