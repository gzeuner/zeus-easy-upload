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
- `connector`: Source-/Target-Adapter fuer Datenfluesse
- `flow`: neutrales Record- und Flow-Modell
- `domain`: Request/Result/Fehlermodelle
- `util`: Identifier- und Spaltensanitizing fÃ¼r AS400-Regeln
- `config`: Konfigurationseigenschaften (`app.*`)

## Connector Architecture

`zeus-easy-upload` entwickelt sich schrittweise von einem CSV->DB Tool zu einem allgemeinen Datenfluss-Werkzeug mit mehreren Connectoren.

Aktueller First-Class-Flow:

- CSV -> DB table
- filesystem CSV source/target for bounded local staging
- REST JSON source/target with explicit deployment and security policy

Minimale Architektur in der aktuellen Iteration:

- `SourceConnector -> DataFlow -> TargetConnector`
- `FlowConfiguration` beschreibt den aktuellen Source-/Target-Aufbau neutral
- `CsvSourceConnector` liest das bereits bewaehrte `ParsedCsv`
- `DbTableTargetConnector` delegiert weiterhin an die bestehende `ImportService`-Logik

Wichtig:

- die bestehende CSV->DB Funktionalitaet bleibt erhalten
- vorhandene Services fuer Parsing, Mapping, Metadata und Import bleiben das Rueckgrat der Implementierung
- die Connector-Abstraktion ist bewusst inkrementell und noch kein Plugin-System

Naechste Connector-Richtungen:

- FTP
- SFTP
- cloud/object storage

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
  connection-profile-directory: connections
  connection-master-key: ${ZEUS_CONNECTION_MASTER_KEY:}
```

## GUI und Verbindungsprofile

Die Anwendung bietet unter /connections eine erweiterbare GUI fuer DB2/400- und
REST-Verbindungsprofile. Profile enthalten nur Metadaten; Zugangsdaten werden
mit AES-256-GCM verschluesselt. Der Master-Key wird ausschliesslich ueber
ZEUS_CONNECTION_MASTER_KEY oder app.connection-master-key bereitgestellt.

Fuer lokale Entwicklungsprofile kann APP_CONNECTION_PROFILE_DIRECTORY=.local/connections
gesetzt werden. Das Verzeichnis .local/ ist absichtlich git-ignoriert und darf
nicht versioniert werden. Der Master-Key muss ausserhalb des Repositories
gesichert werden.

IBM-i JDBC-URLs duerfen Treiberattribute wie translate binary=true enthalten;
diese URL wird zur Validierung sicher behandelt und unveraendert an den Treiber
weitergegeben. Die Verbindungsauswahl aus gespeicherten Profilen und ein
dedizierter GUI-Verbindungstest sind noch separate Erweiterungspakete.

## IBM i JDBC Hinweise

- Schema entspricht `Library`.
- SQL arbeitet mit `LIB.TABLE`.
- Identifier werden in GroÃŸbuchstaben normalisiert.
- Spaltennamen werden auf `A-Z0-9_` reduziert.
- SpaltenlÃ¤nge wird auf 10 Zeichen begrenzt (Trunkierung + Hash-Suffix).
- Leere Strings werden als `NULL` gespeichert.

## Starten

```bash
mvn clean package
mvn spring-boot:run
```

Anwendung: `http://localhost:8080`

## Nutzung

1. CSV hochladen, Library und Tabellenname eingeben.
2. Vorschau prÃ¼fen und Typen/Parameter bei Bedarf anpassen.
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
- Analyse Ã¼ber maximal `app.sample-rows` Zeilen (Default: 200)
- Datum: `yyyy-MM-dd`, `dd.MM.yyyy`, `dd/MM/yyyy`
- Timestamp: `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd'T'HH:mm:ss`
- `VARCHAR(n)`: Max-LÃ¤nge + 10%, Minimum 32, Maximum 4000

## Beispiel-CSV

`src/main/resources/examples/sample.csv`

## Tests

Enthaltene Tests decken neben dem Importkern auch Filesystem- und REST-Connectoren,
verschluesselte Verbindungsprofile sowie GUI-nahe Profilvalidierung ab.

Beispielhafte Unit-Tests:
- `ColumnNameSanitizerTest`
- `TypeInferenceServiceTest`
- `DecimalDetectionTest`
- `DateParsingTest`

AusfÃ¼hren:

```bash
mvn test
```

## Screenshots

Optional: Screenshots von `index`, `preview`, `result` kÃ¶nnen spÃ¤ter ergÃ¤nzt werden.

## Erweiterbarkeit (Future)

- Update/Upsert (Merge-Strategie)
- Mehrere Delimiter-Profile
- Persistente Job-Historie
- Fehlerexport als CSV/JSON

## Metadata endpoints

Fuer kommende V2-Mapping-Features stellt die Anwendung zusaetzliche, read-only Metadata-Endpunkte bereit:

- `/meta/tables?library=LIB`
- `/meta/columns?library=LIB&table=TABLE`

