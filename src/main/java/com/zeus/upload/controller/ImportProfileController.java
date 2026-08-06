package com.zeus.upload.controller;

import com.zeus.upload.domain.ImportProfile;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.service.ImportProfileService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
public class ImportProfileController {

    private final ImportProfileService profileService;

    public ImportProfileController(ImportProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public List<String> list() throws IOException {
        return profileService.list();
    }

    @GetMapping("/{name}")
    public ImportProfile load(@PathVariable String name) throws IOException {
        return profileService.load(name);
    }

    @PostMapping
    public ResponseEntity<ImportProfile> save(
            @RequestParam String name,
            @RequestBody ImportRequest request
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.save(name, request));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) throws IOException {
        profileService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
