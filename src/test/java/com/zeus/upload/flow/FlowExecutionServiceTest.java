package com.zeus.upload.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.connector.ConnectorFactory;
import com.zeus.upload.connector.ImportResultAwareTargetConnector;
import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.domain.ImportResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FlowExecutionServiceTest {

    @Test
    void shouldExecuteConfiguredFlowAndReturnTargetResult() {
        ImportResult expected = ImportResult.success("ok", "", 1);
        ConnectorFactory connectorFactory = new StubConnectorFactory(expected);
        FlowExecutionService service = new FlowExecutionService(connectorFactory);
        FlowConfiguration configuration = new FlowConfiguration(new StubSourceConfiguration(), new StubTargetConfiguration());

        ImportResult result = service.execute(configuration);

        assertThat(result).isSameAs(expected);
    }

    private static class StubConnectorFactory extends ConnectorFactory {

        private final ImportResult result;

        private StubConnectorFactory(ImportResult result) {
            super(null);
            this.result = result;
        }

        @Override
        public SourceConnector createSource(SourceConfiguration configuration) {
            return Stream::empty;
        }

        @Override
        public TargetConnector createTarget(TargetConfiguration configuration) {
            return new StubTargetConnector(result);
        }
    }

    private record StubSourceConfiguration() implements SourceConfiguration {
    }

    private record StubTargetConfiguration() implements TargetConfiguration {
    }

    private static class StubTargetConnector implements ImportResultAwareTargetConnector {

        private final ImportResult result;

        private StubTargetConnector(ImportResult result) {
            this.result = result;
        }

        @Override
        public void write(Stream<com.zeus.upload.flow.DataRecord> records) {
            records.count();
        }

        @Override
        public ImportResult getImportResult() {
            return result;
        }
    }
}
