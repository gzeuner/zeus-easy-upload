package com.zeus.upload.connector;

import com.zeus.upload.domain.ImportResult;

public interface ImportResultAwareTargetConnector extends TargetConnector {

    ImportResult getImportResult();
}
