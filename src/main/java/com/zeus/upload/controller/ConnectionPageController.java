package com.zeus.upload.controller;

import com.zeus.upload.service.ConnectionCryptoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConnectionPageController {

    private final ConnectionCryptoService cryptoService;

    public ConnectionPageController(ConnectionCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @GetMapping("/connections")
    public String connections(Model model) {
        model.addAttribute("encryptionConfigured", cryptoService.isConfigured());
        return "connections";
    }
}
