package com.zeus.upload.flow;

import com.zeus.upload.connector.ConnectorFactory;
import com.zeus.upload.connector.ImportResultAwareTargetConnector;
import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.domain.ImportResult;
import org.springframework.stereotype.Service;

@Service
public class FlowExecutionService {

    private final ConnectorFactory connectorFactory;

    public FlowExecutionService(ConnectorFactory connectorFactory) {
        this.connectorFactory = connectorFactory;
    }

    public ImportResult execute(FlowConfiguration configuration) {
        SourceConnector source = connectorFactory.createSource(configuration.getSource());
        TargetConnector target = connectorFactory.createTarget(configuration.getTarget());
        new DataFlow(source, target).execute();

        if (target instanceof ImportResultAwareTargetConnector resultAwareTarget) {
            return resultAwareTarget.getImportResult();
        }
        throw new IllegalStateException("Configured target does not provide an ImportResult.");
    }
}
