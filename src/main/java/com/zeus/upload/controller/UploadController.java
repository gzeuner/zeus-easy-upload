package com.zeus.upload.controller;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.PreviewContext;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.ImportService;
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
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@SessionAttributes("previewContext")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final List<String> SUPPORTED_TYPES = List.of("INTEGER", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP", "VARCHAR");

    private final CsvParsingService csvParsingService;
    private final ImportService importService;
    private final AppProperties appProperties;

    public UploadController(CsvParsingService csvParsingService, ImportService importService, AppProperties appProperties) {
        this.csvParsingService = csvParsingService;
        this.importService = importService;
        this.appProperties = appProperties;
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
        return "index";
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("library") String library,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "dropAndRecreate", defaultValue = "false") boolean dropAndRecreate,
            @ModelAttribute("previewContext") PreviewContext previewContext,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a CSV file.");
            return "redirect:/";
        }

        try {
            var parsed = csvParsingService.parse(file);
            ImportRequest request = new ImportRequest();
            request.setLibrary(library);
            request.setTableName(tableName);
            request.setDropAndRecreate(dropAndRecreate);
            request.setColumns(copyColumns(parsed.getProposals()));

            previewContext.setParsedCsv(parsed);
            previewContext.setOriginalFilename(file.getOriginalFilename());

            model.addAttribute("importRequest", request);
            model.addAttribute("previewErrors", parsed.getParseErrors());
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            return "preview";
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Upload parsing failed", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "CSV parse failed: " + ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/import")
    public String doImport(
            @Valid @ModelAttribute("importRequest") ImportRequest importRequest,
            BindingResult bindingResult,
            @ModelAttribute("previewContext") PreviewContext previewContext,
            Model model,
            SessionStatus sessionStatus
    ) {
        if (previewContext.getParsedCsv() == null) {
            model.addAttribute("errorMessage", "No upload context found. Please upload the CSV again.");
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            return "index";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("supportedTypes", SUPPORTED_TYPES);
            model.addAttribute("previewErrors", previewContext.getParsedCsv().getParseErrors());
            return "preview";
        }

        ImportResult result = importService.importCsv(importRequest, previewContext.getParsedCsv());
        model.addAttribute("result", result);
        sessionStatus.setComplete();
        return "result";
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
}
