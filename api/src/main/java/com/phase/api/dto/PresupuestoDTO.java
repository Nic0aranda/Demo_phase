package com.phase.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class PresupuestoDTO {

    private String id; // Ej: PRE-1001

    @NotBlank(message = "El RUT del cliente es obligatorio")
    private String rutCliente;

    private String fechaEmision;
    
    @NotEmpty(message = "El presupuesto debe tener al menos un ítem")
    private List<ItemPresupuestoDTO> items;

    // Estos valores los recalculará el backend, pero los declaramos para el JSON
    private double subtotal;
    private double impuestos; // 19% IVA
    private double total;
    
    private String estado = "BORRADOR"; // BORRADOR, ENVIADO, APROBADO, RECHAZADO

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRutCliente() { return rutCliente; }
    public void setRutCliente(String rutCliente) { this.rutCliente = rutCliente; }
    public String getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(String fechaEmision) { this.fechaEmision = fechaEmision; }
    public List<ItemPresupuestoDTO> getItems() { return items; }
    public void setItems(List<ItemPresupuestoDTO> items) { this.items = items; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getImpuestos() { return impuestos; }
    public void setImpuestos(double impuestos) { this.impuestos = impuestos; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    // Clase interna para las líneas del presupuesto
    public static class ItemPresupuestoDTO {
        @NotBlank(message = "La descripción no puede estar vacía")
        private String descripcion;

        @Min(value = 1, message = "La cantidad mínima es 1")
        private int cantidad;

        @Min(value = 0, message = "El precio no puede ser negativo")
        private double precioUnitario;

        private double totalLinea; // Recalculado por backend

        // Getters y Setters
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }
        public double getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; }
        public double getTotalLinea() { return totalLinea; }
        public void setTotalLinea(double totalLinea) { this.totalLinea = totalLinea; }
    }
}