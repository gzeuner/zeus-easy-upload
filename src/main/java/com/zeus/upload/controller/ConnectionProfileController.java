package com.zeus.upload.controller;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.service.ConnectionCryptoService;
import com.zeus.upload.service.ConnectionProfileService;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/connections")
public class ConnectionProfileController {

    private final ConnectionProfileService profileService;
    private final ConnectionCryptoService cryptoService;

    public ConnectionProfileController(ConnectionProfileService profileService, ConnectionCryptoService cryptoService) {
        this.profileService = profileService;
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public List<ConnectionProfile> list() throws IOException {
        return profileService.list();
    }

    @GetMapping("/{name}")
    public ConnectionProfile load(@PathVariable String name) throws IOException {
        try {
            return profileService.load(name);
        } catch (NoSuchFileException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection profile not found");
        }
    }

    @PostMapping
    public ResponseEntity<ConnectionProfile> save(@RequestBody ConnectionProfileRequest request) throws IOException {
        if (!cryptoService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Connection encryption is not configured");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(profileService.save(request));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) throws IOException {
        profileService.delete(name);
        return ResponseEntity.noContent().build();
    }
}
