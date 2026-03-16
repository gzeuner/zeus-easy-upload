package com.zeus.upload.connector.file;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.DataRecord;
import java.util.List;

public class CsvSourceConnector implements SourceConnector {

    private final ParsedCsv parsedCsv;
    private final List<String> recordKeys;

    public CsvSourceConnector(ParsedCsv parsedCsv) {
        this.parsedCsv = parsedCsv;
        this.recordKeys = buildRecordKeys(parsedCsv);
    }

    @Override
    public java.util.stream.Stream<DataRecord> read() {
        return parsedCsv.getRows().stream().map(this::toRecord);
    }

    private DataRecord toRecord(List<String> row) {
        DataRecord record = new DataRecord();
        for (int index = 0; index < recordKeys.size(); index++) {
            String value = index < row.size() ? row.get(index) : null;
            record.set(recordKeys.get(index), value);
        }
        return record;
    }

    private List<String> buildRecordKeys(ParsedCsv csv) {
        if (csv.getProposals().isEmpty()) {
            return csv.getOriginalHeaders().stream()
                    .map(header -> header == null ? "" : header)
                    .toList();
        }

        return csv.getProposals().stream()
                .map(this::resolveRecordKey)
                .toList();
    }

    private String resolveRecordKey(ColumnProposal proposal) {
        if (proposal.getFinalName() != null && !proposal.getFinalName().isBlank()) {
            return proposal.getFinalName();
        }
        if (proposal.getSanitizedName() != null && !proposal.getSanitizedName().isBlank()) {
            return proposal.getSanitizedName();
        }
        if (proposal.getOriginalName() != null && !proposal.getOriginalName().isBlank()) {
            return proposal.getOriginalName();
        }
        return "COLUMN_" + proposal.getIndex();
    }
}
