package com.phase.api.controller;

import com.phase.api.service.RecalculoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administracion", description = "Tareas de mantenimiento y reparacion de datos")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final RecalculoService recalculoService;

    public AdminController(RecalculoService recalculoService) {
        this.recalculoService = recalculoService;
    }

    @PostMapping("/recalcular")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> recalcular() {
        return ResponseEntity.ok(recalculoService.recalcularTodas());
    }
}
