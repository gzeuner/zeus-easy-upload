package com.zeus.upload.controller;

import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.DbTableRef;
import com.zeus.upload.service.MetadataService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/meta")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/tables")
    public List<DbTableRef> listTables(@RequestParam("library") String library) {
        if (!StringUtils.hasText(library)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "library must not be blank");
        }
        return metadataService.listTables(library);
    }

    @GetMapping("/columns")
    public List<DbColumnMeta> listColumns(
            @RequestParam("library") String library,
            @RequestParam("table") String table
    ) {
        if (!StringUtils.hasText(library)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "library must not be blank");
        }
        if (!StringUtils.hasText(table)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "table must not be blank");
        }
        return metadataService.listColumns(library, table);
    }
}
