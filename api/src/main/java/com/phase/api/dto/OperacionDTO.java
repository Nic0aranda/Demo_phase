package com.phase.api.dto;

import jakarta.validation.constraints.NotBlank;

public class OperacionDTO {
    private String id;
    @NotBlank(message = "El RUT es obligatorio")
    private String rutCliente;
    private String idPresupuestoOrigen; // Opcional
    @NotBlank(message = "Debe especificar el tipo de servicio")
    private String tipoServicio;
    private String fechaInicio;
    private String estado = "PLANIFICADA"; // PLANIFICADA, EN_CURSO, FINALIZADA, CANCELADA
    private String responsableAsignado;

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRutCliente() { return rutCliente; }
    public void setRutCliente(String rutCliente) { this.rutCliente = rutCliente; }
    public String getIdPresupuestoOrigen() { return idPresupuestoOrigen; }
    public void setIdPresupuestoOrigen(String idPresupuestoOrigen) { this.idPresupuestoOrigen = idPresupuestoOrigen; }
    public String getTipoServicio() { return tipoServicio; }
    public void setTipoServicio(String tipoServicio) { this.tipoServicio = tipoServicio; }
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getResponsableAsignado() { return responsableAsignado; }
    public void setResponsableAsignado(String responsableAsignado) { this.responsableAsignado = responsableAsignado; }
}