package com.fisioagenda.model;

import java.time.LocalDateTime;

/**
 * Usuario del sistema (personal de la clínica). La contraseña nunca se guarda en claro:
 * solo se almacena su hash PBKDF2 y la sal aleatoria con la que se calculó.
 */
public class Usuario {
    private int id;
    private String username;
    private String nombreCompleto;
    private String passwordHash;
    private String salt;
    private Rol rol;
    private boolean activo = true;
    private int intentosFallidos;
    private LocalDateTime bloqueadoHasta;

    public Usuario() {
    }

    public Usuario(int id, String username, String nombreCompleto, Rol rol) {
        this.id = id;
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }

    /** Indica si la cuenta está bloqueada temporalmente en el instante indicado. */
    public boolean estaBloqueado(LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    @Override
    public String toString() {
        return nombreCompleto;
    }
}
