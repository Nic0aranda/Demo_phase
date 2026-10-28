package com.phase.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ClienteDTO {
    
    @NotBlank(message = "El RUT es obligatorio")
    @Pattern(regexp = "^[0-9]+-[0-9kK]{1}$", message = "Formato de RUT inválido")
    private String rut;
    
    @NotBlank(message = "El nombre o razón social es obligatorio")
    private String nombre;
    
    @Email(message = "Debe ser un correo válido")
    private String emailFinanzas;
    
    private String telefono;
    private String direccion;
    private boolean activo = true;

    // Getters y Setters
    public String getRut() { return rut; }
    public void setRut(String rut) { this.rut = rut; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmailFinanzas() { return emailFinanzas; }
    public void setEmailFinanzas(String emailFinanzas) { this.emailFinanzas = emailFinanzas; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}