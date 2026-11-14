package com.phase.api.dto;

import java.util.List;

/**
 * Resultado del envio simulado de una factura por correo. No se conecta a un
 * servidor SMTP: el envio queda registrado en Phase con estado SIMULADO.
 */
public record EnvioFacturaDTO(
        String id,
        String facturaId,
        String destinatario,
        String asunto,
        String mensaje,
        String fechaEnvio,
        String estado,
        List<String> adjuntos
) {
}
