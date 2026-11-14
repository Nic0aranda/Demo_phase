package com.phase.api.controller;

import com.phase.api.dto.CuentaPorCobrarDTO;
import com.phase.api.dto.ResumenCarteraDTO;
import com.phase.api.service.CarteraService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cuentas-por-cobrar")
@Tag(name = "Cuentas por cobrar", description = "Cartera derivada de facturas, pagos y notas de credito")
@SecurityRequirement(name = "bearerAuth")
public class CarteraController {

    private final CarteraService carteraService;

    public CarteraController(CarteraService carteraService) {
        this.carteraService = carteraService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CuentaPorCobrarDTO>> listar() {
        return ResponseEntity.ok(carteraService.listar());
    }

    @GetMapping("/resumen")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResumenCarteraDTO> resumen() {
        return ResponseEntity.ok(carteraService.resumen());
    }

    @GetMapping("/{facturaId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CuentaPorCobrarDTO> obtener(@PathVariable String facturaId) {
        return ResponseEntity.ok(carteraService.obtener(facturaId));
    }
}
