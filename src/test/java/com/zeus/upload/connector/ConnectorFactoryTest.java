package com.zeus.upload.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zeus.upload.connector.db.DbTableTargetConnector;
import com.zeus.upload.connector.file.CsvSourceConnector;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.CsvSourceConfiguration;
import com.zeus.upload.flow.DbTableTargetConfiguration;
import com.zeus.upload.flow.DbTableWriteMode;
import com.zeus.upload.service.ImportService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectorFactoryTest {

    @Test
    void shouldCreateCsvSourceConnector() {
        ConnectorFactory factory = new ConnectorFactory(mock(ImportService.class));
        ParsedCsv parsedCsv = new ParsedCsv();

        SourceConnector connector = factory.createSource(new CsvSourceConfiguration(parsedCsv));

        assertThat(connector).isInstanceOf(CsvSourceConnector.class);
    }

    @Test
    void shouldCreateDbTableTargetConnector() {
        ConnectorFactory factory = new ConnectorFactory(mock(ImportService.class));
        DbTableTargetConfiguration configuration = new DbTableTargetConfiguration(
                "TESTLIB",
                "ORDERS",
                DbTableWriteMode.CREATE_TABLE,
                false,
                List.of(column()),
                List.of(),
                List.of(),
                List.of()
        );

        TargetConnector connector = factory.createTarget(configuration);

        assertThat(connector).isInstanceOf(DbTableTargetConnector.class);
    }

    private ColumnProposal column() {
        ColumnProposal proposal = new ColumnProposal();
        proposal.setIndex(0);
        proposal.setOriginalName("ID");
        proposal.setSanitizedName("ID");
        proposal.setFinalName("ID");
        proposal.setSqlType("INTEGER");
        return proposal;
    }
}
