package com.phase.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClienteDTO {

    @NotBlank(message = "El nombre o razon social es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
    private String nombre;

    @Size(max = 250, message = "La glosa no puede superar 250 caracteres")
    private String glosa;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "(?i)RUT|PASAPORTE|DNI", message = "Tipo de documento invalido")
    private String tipoDoc = "RUT";

    @NotBlank(message = "El numero de documento es obligatorio")
    @Size(max = 20, message = "El numero de documento es demasiado largo")
    @JsonAlias("rut")
    private String numDoc;

    @NotBlank(message = "El pais es obligatorio")
    @Pattern(regexp = "(?i)[a-z]{2}", message = "El pais debe tener 2 letras")
    private String pais = "cl";

    @NotBlank(message = "La region es obligatoria")
    @Size(max = 80, message = "La region es demasiado larga")
    private String region;

    @NotBlank(message = "La direccion es obligatoria")
    @Size(max = 200, message = "La direccion es demasiado larga")
    private String direccion;

    @Size(max = 30, message = "El telefono es demasiado largo")
    private String telefono;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no es valido")
    @Size(max = 120, message = "El email es demasiado largo")
    @JsonAlias("emailFinanzas")
    private String email;

    private boolean activo = true;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getGlosa() {
        return glosa;
    }

    public void setGlosa(String glosa) {
        this.glosa = glosa;
    }

    public String getTipoDoc() {
        return tipoDoc;
    }

    public void setTipoDoc(String tipoDoc) {
        this.tipoDoc = tipoDoc;
    }

    public String getNumDoc() {
        return numDoc;
    }

    public void setNumDoc(String numDoc) {
        this.numDoc = numDoc;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
