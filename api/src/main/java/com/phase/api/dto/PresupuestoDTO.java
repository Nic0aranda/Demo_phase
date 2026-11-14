package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PresupuestoDTO {

    private String id;

    @NotBlank(message = "El RUT del cliente es obligatorio")
    private String rutCliente;

    private String fechaEmision;

    @NotEmpty(message = "El presupuesto debe tener al menos un item")
    @Valid
    private List<ItemPresupuestoDTO> items;

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal impuestos = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    private String estado = "BORRADOR";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRutCliente() {
        return rutCliente;
    }

    public void setRutCliente(String rutCliente) {
        this.rutCliente = rutCliente;
    }

    public String getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(String fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public List<ItemPresupuestoDTO> getItems() {
        return items;
    }

    public void setItems(List<ItemPresupuestoDTO> items) {
        this.items = items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getImpuestos() {
        return impuestos;
    }

    public void setImpuestos(BigDecimal impuestos) {
        this.impuestos = impuestos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public static class ItemPresupuestoDTO {

        @NotBlank(message = "La descripcion no puede estar vacia")
        private String descripcion;

        @Min(value = 1, message = "La cantidad minima es 1")
        private int cantidad;

        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        private BigDecimal precioUnitario = BigDecimal.ZERO;

        private BigDecimal totalLinea = BigDecimal.ZERO;

        public String getDescripcion() {
            return descripcion;
        }

        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }

        public int getCantidad() {
            return cantidad;
        }

        public void setCantidad(int cantidad) {
            this.cantidad = cantidad;
        }

        public BigDecimal getPrecioUnitario() {
            return precioUnitario;
        }

        public void setPrecioUnitario(BigDecimal precioUnitario) {
            this.precioUnitario = precioUnitario;
        }

        public BigDecimal getTotalLinea() {
            return totalLinea;
        }

        public void setTotalLinea(BigDecimal totalLinea) {
            this.totalLinea = totalLinea;
        }
    }
}
