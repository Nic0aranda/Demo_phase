package com.phase.api.common;

/**
 * Se publica despues de cualquier movimiento que pueda cambiar el saldo de una
 * factura (pago, anulacion de pago, nota de credito o su anulacion).
 */
public record FacturaActualizadaEvent(String facturaId) {
}
