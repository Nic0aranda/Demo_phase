package com.phase.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class FacturaDTO {
    private String id;
    @NotBlank(message = "El RUT es obligatorio")
    private String rutCliente;
    private String idOperacionOrigen; // Opcional
    private String fechaEmision;
    private double subtotal;
    private double impuestos;
    private double total;
    private String estado = "PENDIENTE"; // PENDIENTE, PAGADA, ANULADA
    
    @NotEmpty(message = "La factura debe tener ítems")
    private List<ItemFacturaDTO> items;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRutCliente() { return rutCliente; }
    public void setRutCliente(String rutCliente) { this.rutCliente = rutCliente; }
    public String getIdOperacionOrigen() { return idOperacionOrigen; }
    public void setIdOperacionOrigen(String idOperacionOrigen) { this.idOperacionOrigen = idOperacionOrigen; }
    public String getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(String fechaEmision) { this.fechaEmision = fechaEmision; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getImpuestos() { return impuestos; }
    public void setImpuestos(double impuestos) { this.impuestos = impuestos; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public List<ItemFacturaDTO> getItems() { return items; }
    public void setItems(List<ItemFacturaDTO> items) { this.items = items; }

    public static class ItemFacturaDTO {
        @NotBlank private String descripcion;
        @Min(1) private int cantidad;
        @Min(0) private double precioUnitario;
        private double totalLinea;

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