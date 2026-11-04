package com.phase.api.usuario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phase.api.security.EstadoUsuario;
import com.phase.api.security.Rol;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioRecord {

    private String email;
    private String nombres;
    private String apellidos;
    private Rol rol = Rol.COMERCIAL;
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;
    private List<String> permisos = new ArrayList<>();
    private String passwordHash;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public void setEstado(EstadoUsuario estado) {
        this.estado = estado;
    }

    public List<String> getPermisos() {
        return permisos;
    }

    public void setPermisos(List<String> permisos) {
        this.permisos = permisos;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNombreCompleto() {
        String nombre = nombres == null ? "" : nombres.trim();
        String apellido = apellidos == null ? "" : apellidos.trim();
        return (nombre + " " + apellido).trim();
    }
}
