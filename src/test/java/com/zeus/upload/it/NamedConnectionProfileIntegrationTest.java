package com.zeus.upload.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.service.ConnectionProfileService;
import com.zeus.upload.service.ImportService;
import com.zeus.upload.service.MetadataService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies imports can target a named connection profile (separate H2 URL)
 * while the bootstrap DataSource remains the default Spring pool.
 */
@SpringBootTest
@ActiveProfiles("test")
class NamedConnectionProfileIntegrationTest {

    private static final String PROFILE = "h2-named-it";
    private static final String NAMED_URL =
            "jdbc:h2:mem:zeus_named_profile_it;MODE=DB2;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true";

    @Autowired
    private ConnectionProfileService connectionProfileService;

    @Autowired
    private ImportService importService;

    @Autowired
    private MetadataService metadataService;

    @Test
    void importUsesNamedConnectionProfileNotOnlyBootstrap() throws Exception {
        ConnectionProfileRequest request = new ConnectionProfileRequest();
        request.setName(PROFILE);
        request.setType(ConnectionType.DB2_400);
        request.setEndpoint(NAMED_URL);
        request.setDescription("integration named H2");
        request.setCredentials(Map.of("username", "sa", "secret", ""));
        connectionProfileService.save(request);

        ImportRequest importRequest = new ImportRequest();
        importRequest.setLibrary("NAMEDLIB");
        importRequest.setTableName("NAMED_IMPORT");
        importRequest.setConnectionProfileName(PROFILE);
        importRequest.setDropAndRecreate(true);
        importRequest.setColumns(List.of(
                proposal("ID", "INTEGER"),
                proposal("NAME", "VARCHAR")
        ));
        importRequest.getColumns().get(1).setLength(64);

        ParsedCsv csv = new ParsedCsv();
        csv.getOriginalHeaders().addAll(List.of("id", "name"));
        csv.getRows().add(List.of("1", "Named"));

        ImportResult result = importService.importCsv(importRequest, csv);
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(result.getInsertedRows()).isEqualTo(1);

        // Data must exist on the named DB, not only conceptually.
        DriverManagerDataSource namedDs = new DriverManagerDataSource();
        namedDs.setUrl(NAMED_URL);
        namedDs.setUsername("sa");
        namedDs.setPassword("");
        namedDs.setDriverClassName("org.h2.Driver");
        JdbcTemplate namedJdbc = new JdbcTemplate(namedDs);
        Integer count = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM \"NAMEDLIB\".\"NAMED_IMPORT\"", Integer.class);
        assertThat(count).isEqualTo(1);

        assertThat(metadataService.listTables("NAMEDLIB", PROFILE).stream()
                .map(ref -> ref.getTableName()).toList())
                .contains("NAMED_IMPORT");
    }

    private static ColumnProposal proposal(String name, String type) {
        ColumnProposal p = new ColumnProposal();
        p.setIndex(0);
        p.setOriginalName(name);
        p.setSanitizedName(name);
        p.setFinalName(name);
        p.setSqlType(type);
        p.setDetectedType(type);
        p.setNullable(true);
        return p;
    }
}
