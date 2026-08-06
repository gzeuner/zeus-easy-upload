package com.zeus.upload.controller;

import com.zeus.upload.domain.CsvImportOptions;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportJob;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.domain.PreviewContext;
import com.zeus.upload.flow.FlowConfigurationFactory;
import com.zeus.upload.flow.FlowExecutionService;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.ImportJobService;
import com.zeus.upload.service.MappingService;
import com.zeus.upload.service.MetadataService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jobs")
public class ImportJobController {

    private final CsvParsingService csvParsingService;
    private final MetadataService metadataService;
    private final MappingService mappingService;
    private final FlowConfigurationFactory configurationFactory;
    private final FlowExecutionService flowExecutionService;
    private final ImportJobService jobService;

    public ImportJobController(
            CsvParsingService csvParsingService,
            MetadataService metadataService,
            MappingService mappingService,
            FlowConfigurationFactory configurationFactory,
            FlowExecutionService flowExecutionService,
            ImportJobService jobService
    ) {
        this.csvParsingService = csvParsingService;
        this.metadataService = metadataService;
        this.mappingService = mappingService;
        this.configurationFactory = configurationFactory;
        this.flowExecutionService = flowExecutionService;
        this.jobService = jobService;
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ImportJob> submit(
            @RequestParam MultipartFile file,
            @RequestParam String library,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "INSERT") String operation,
            @RequestParam(required = false) String existingTableName,
            @RequestParam(required = false) List<String> keyColumns,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) String csvDelimiter,
            @RequestParam(required = false) String csvEncoding,
            @RequestParam(required = false) String csvQuote
    ) throws IOException {
        CsvImportOptions options = new CsvImportOptions();
        options.setDelimiter(csvDelimiter);
        options.setEncoding(csvEncoding);
        options.setQuote(csvQuote);
        ParsedCsv parsed = csvParsingService.parse(file, options);
        boolean existing = existingTableName != null && !existingTableName.isBlank();
        ImportRequest request = new ImportRequest();
        request.setLibrary(library);
        request.setTableName(existing ? existingTableName : tableName);
        request.setExistingTableName(existing ? existingTableName : null);
        request.setUseExistingTable(existing);
        request.setOperation(existing ? operation : "CREATE");
        request.setDryRun(dryRun);
        request.setColumns(parsed.getProposals());
        request.setKeyColumns(keyColumns);
        request.setCsvDelimiter(csvDelimiter);
        request.setCsvEncoding(csvEncoding);
        request.setCsvQuote(csvQuote);

        PreviewContext context = new PreviewContext();
        context.setParsedCsv(parsed);
        if (existing) {
            List<DbColumnMeta> columns = metadataService.listColumns(library, existingTableName);
            context.setDbColumns(columns);
            context.setMappings(mappingService.autoMap(parsed, columns));
            request.setMappings(context.getMappings());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.submit(
                () -> flowExecutionService.execute(configurationFactory.fromImportContext(request, context))));
    }

    @GetMapping
    public List<ImportJob> list() {
        return jobService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportJob> get(@PathVariable String id) {
        ImportJob job = jobService.get(id);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        return jobService.cancel(id) ? ResponseEntity.accepted().build() : ResponseEntity.notFound().build();
    }
}
