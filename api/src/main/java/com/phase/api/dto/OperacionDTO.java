package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OperacionDTO {

    private String id;

    @NotBlank(message = "El RUT del cliente es obligatorio")
    private String rutCliente;

    private String idPresupuestoOrigen;

    @NotBlank(message = "Debe especificar el tipo de operacion")
    @Pattern(regexp = "(?i)IMPORTACION|EXPORTACION|NACIONAL", message = "Tipo de operacion invalido")
    @JsonAlias("tipoServicio")
    private String tipoOperacion;

    @Pattern(regexp = "(?i)MARITIMA|AEREA|TERRESTRE|MULTIMODAL", message = "Via de transporte invalida")
    private String viaTransporte;

    @Size(max = 60, message = "La referencia es demasiado larga")
    private String referencia;

    @Size(max = 500, message = "Las observaciones son demasiado largas")
    private String observaciones;

    private String fechaInicio;

    private String estado = "PLANIFICADA";

    private String responsableAsignado;

    @Valid
    private List<DocumentoOperacionDTO> documentos = new ArrayList<>();

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

    public String getIdPresupuestoOrigen() {
        return idPresupuestoOrigen;
    }

    public void setIdPresupuestoOrigen(String idPresupuestoOrigen) {
        this.idPresupuestoOrigen = idPresupuestoOrigen;
    }

    public String getTipoOperacion() {
        return tipoOperacion;
    }

    public void setTipoOperacion(String tipoOperacion) {
        this.tipoOperacion = tipoOperacion;
    }

    public String getViaTransporte() {
        return viaTransporte;
    }

    public void setViaTransporte(String viaTransporte) {
        this.viaTransporte = viaTransporte;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(String fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getResponsableAsignado() {
        return responsableAsignado;
    }

    public void setResponsableAsignado(String responsableAsignado) {
        this.responsableAsignado = responsableAsignado;
    }

    public List<DocumentoOperacionDTO> getDocumentos() {
        return documentos;
    }

    public void setDocumentos(List<DocumentoOperacionDTO> documentos) {
        this.documentos = documentos;
    }

    public static class DocumentoOperacionDTO {

        @NotBlank(message = "El tipo de documento es obligatorio")
        private String tipo;

        @NotBlank(message = "El numero de documento es obligatorio")
        private String numero;

        private String fecha;
        private String archivo;

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getNumero() {
            return numero;
        }

        public void setNumero(String numero) {
            this.numero = numero;
        }

        public String getFecha() {
            return fecha;
        }

        public void setFecha(String fecha) {
            this.fecha = fecha;
        }

        public String getArchivo() {
            return archivo;
        }

        public void setArchivo(String archivo) {
            this.archivo = archivo;
        }
    }
}
