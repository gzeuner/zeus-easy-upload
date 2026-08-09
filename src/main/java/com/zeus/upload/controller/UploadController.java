package com.zeus.upload.controller;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.CsvImportOptions;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.MappingValidationResult;
import com.zeus.upload.domain.PreviewContext;
import com.zeus.upload.flow.FlowConfigurationFactory;
import com.zeus.upload.flow.FlowExecutionService;
import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.service.ConnectionProfileService;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.MappingService;
import com.zeus.upload.service.MetadataService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.StringUtils;

@Controller
@SessionAttributes({"previewContext", "lastImportResult"})
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final List<String> SUPPORTED_TYPES = List.of("INTEGER", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP", "VARCHAR");

    private final CsvParsingService csvParsingService;
    private final MetadataService metadataService;
    private final MappingService mappingService;
    private final AppProperties appProperties;
    private final FlowConfigurationFactory flowConfigurationFactory;
    private final FlowExecutionService flowExecutionService;
    private final ConnectionProfileService connectionProfileService;

    public UploadController(
            CsvParsingService csvParsingService,
            MetadataService metadataService,
            MappingService mappingService,
            AppProperties appProperties,
            FlowConfigurationFactory flowConfigurationFactory,
            FlowExecutionService flowExecutionService,
            ConnectionProfileService connectionProfileService
    ) {
        this.csvParsingService = csvParsingService;
        this.metadataService = metadataService;
        this.mappingService = mappingService;
        this.appProperties = appProperties;
        this.flowConfigurationFactory = flowConfigurationFactory;
        this.flowExecutionService = flowExecutionService;
        this.connectionProfileService = connectionProfileService;
    }

    @ModelAttribute("previewContext")
    public PreviewContext previewContext() {
        return new PreviewContext();
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("importRequest")) {
            ImportRequest request = new ImportRequest();
            request.setLibrary(appProperties.getDefaultLibrary());
            model.addAttribute("importRequest", request);
        }
        model.addAttribute("supportedTypes", SUPPORTED_TYPES);
        model.addAttribute("jdbcConnections", listJdbcConnections());
        return "index";
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("library") String library,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "dropAndRecreate", defaultValue = "false") boolean dropAndRecreate,
            @RequestParam(value = "useExistingTable", defaultValue = "false") boolean useExistingTable,
            @RequestParam(value = "existingTableName", required = false) String existingTableName,
            @RequestParam(value = "connectionProfileName", required = false) String connectionProfileName,
            @RequestParam(value = "csvDelimiter", required = false) String csvDelimiter,
            @RequestParam(value = "csvEncoding", required = false) String csvEncoding,
            @RequestParam(value = "csvQuote", required = false) String csvQuote,
            @ModelAttribute("previewContext") PreviewContext previewContext,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a CSV file.");
            return "redirect:/";
        }

        try {
            CsvImportOptions csvOptions = new CsvImportOptions();
            csvOptions.setDelimiter(csvDelimiter);
            csvOptions.setEncoding(csvEncoding);
            csvOptions.setQuote(csvQuote);
            var parsed = isDefaultCsvOptions(csvOptions)
                    ? csvParsingService.parse(file)
                    : csvParsingService.parse(file, csvOptions);
            ImportRequest request = new ImportRequest();
            request.setLibrary(library);
            request.setTableName(tableName);
            request.setDropAndRecreate(dropAndRecreate);
            request.setUseExistingTable(useExistingTable);
            if (useExistingTable && StringUtils.hasText(existingTableName)) {
                request.setExistingTableName(existingTableName.trim());
                if (!StringUtils.hasText(tableName)) {
                    request.setTableName(existingTableName.trim());
                }
            }
            request.setColumns(copyColumns(parsed.getProposals()));
            request.setUpsertEnabled(false);
            request.setOperation(useExistingTable ? "INSERT" : "CREATE");
            request.setKeyColumns(List.of());
            request.setCsvDelimiter(csvDelimiter);
            request.setCsvEncoding(csvEncoding);
            request.setCsvQuote(csvQuote);
            if (StringUtils.hasText(connectionProfileName)) {
                request.setConnectionProfileName(connectionProfileName.trim());
            }

            if (useExistingTable && StringUtils.hasText(existingTableName)) {
                List<DbColumnMeta> dbColumns = metadataService.listColumns(
                        library, existingTableName, request.getConnectionProfileName());
                List<ColumnMapping> mappings = mappingService.autoMap(parsed, dbColumns);
                request.setMappings(copyMappings(mappings));
                previewContext.setDbColumns(dbColumns);
                previewContext.setMappings(copyMappings(mappings));
                previewContext.setUseExistingTable(true);
                previewContext.setExistingTableName(existingTableName.trim());
                previewContext.setUpsertEnabled(false);
                previewContext.setKeyColumns(List.of());
                model.addAttribute("dbColumns", dbColumns);
                model.addAttribute("mappings", mappings);
            } else {
                request.setUseExistingTable(false);
                previewContext.setUseExistingTable(false);
                previewContext.setExistingTableName(null);
                previewContext.setUpsertEnabled(false);
                previewContext.setDbColumns(List.of());
                previewContext.setMappings(List.of());
                previewContext.setKeyColumns(List.of());
            }

            previewContext.setParsedCsv(parsed);
            previewContext.setOriginalFilename(file.getOriginalFilename());

            model.addAttribute("importRequest", request);
            model.addAttribute("previewErrors", parsed.getParseErrors());
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            return "preview";
        } catch (IOException | RuntimeException ex) {
            log.warn("Upload preview preparation failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Preview preparation failed: " + ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/import")
    public String doImport(
            @Valid @ModelAttribute("importRequest") ImportRequest importRequest,
            BindingResult bindingResult,
            @ModelAttribute("previewContext") PreviewContext previewContext,
            Model model
    ) {
        if (previewContext.getParsedCsv() == null) {
            model.addAttribute("errorMessage", "No upload context found. Please upload the CSV again.");
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            return "index";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            model.addAttribute("previewErrors", previewContext.getParsedCsv().getParseErrors());
            model.addAttribute("dbColumns", previewContext.getDbColumns());
            model.addAttribute("mappings", importRequest.getMappings());
            return "preview";
        }

        ImportResult result;
        if (importRequest.isUseExistingTable()) {
            List<DbColumnMeta> dbColumns = previewContext.getDbColumns();
            MappingValidationResult validationResult = mappingService.validate(
                    previewContext.getParsedCsv(),
                    dbColumns,
                    importRequest.getMappings(),
                    importRequest.getOperation(),
                    importRequest.getKeyColumns()
            );
            if (!validationResult.isValid()) {
                model.addAttribute("supportedTypes", SUPPORTED_TYPES);
                model.addAttribute("previewErrors", previewContext.getParsedCsv().getParseErrors());
                model.addAttribute("mappingErrors", validationResult.getErrors());
                model.addAttribute("mappingWarnings", validationResult.getWarnings());
                model.addAttribute("dbColumns", dbColumns);
                model.addAttribute("mappings", importRequest.getMappings());
                return "preview";
            }
            model.addAttribute("mappingWarnings", validationResult.getWarnings());
            result = executeConfiguredFlow(importRequest, previewContext);
        } else {
            result = executeConfiguredFlow(importRequest, previewContext);
        }
        model.addAttribute("result", result);
        model.addAttribute("lastImportResult", result);
        return "result";
    }

    private ImportResult executeConfiguredFlow(ImportRequest importRequest, PreviewContext previewContext) {
        return flowExecutionService.execute(flowConfigurationFactory.fromImportContext(importRequest, previewContext));
    }

    private List<ColumnProposal> copyColumns(List<ColumnProposal> source) {
        List<ColumnProposal> copy = new ArrayList<>();
        for (ColumnProposal original : source) {
            ColumnProposal c = new ColumnProposal();
            c.setIndex(original.getIndex());
            c.setOriginalName(original.getOriginalName());
            c.setSanitizedName(original.getSanitizedName());
            c.setFinalName(original.getFinalName());
            c.setSqlType(original.getSqlType());
            c.setDetectedType(original.getDetectedType());
            c.setDuplicate(original.isDuplicate());
            c.setNullable(original.isNullable());
            c.setLength(original.getLength());
            c.setPrecision(original.getPrecision());
            c.setScale(original.getScale());
            copy.add(c);
        }
        return copy;
    }

    private boolean isDefaultCsvOptions(CsvImportOptions options) {
        return !StringUtils.hasText(options.getDelimiter())
                && (!StringUtils.hasText(options.getEncoding()) || "UTF-8".equalsIgnoreCase(options.getEncoding()))
                && (!StringUtils.hasText(options.getQuote()) || "\"".equals(options.getQuote()));
    }

    private List<ConnectionProfile> listJdbcConnections() {
        try {
            return connectionProfileService.list().stream()
                    .filter(profile -> profile.getType() != null && profile.getType().isJdbc())
                    .toList();
        } catch (IOException ex) {
            log.warn("Could not list connection profiles: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ColumnMapping> copyMappings(List<ColumnMapping> source) {
        List<ColumnMapping> copy = new ArrayList<>();
        for (ColumnMapping original : source) {
            ColumnMapping mapping = new ColumnMapping();
            mapping.setCsvColumn(original.getCsvColumn());
            mapping.setCsvIndex(original.getCsvIndex());
            mapping.setTargetColumn(original.getTargetColumn());
            mapping.setIgnored(original.isIgnored());
            mapping.setNote(original.getNote());
            copy.add(mapping);
        }
        return copy;
    }
}
