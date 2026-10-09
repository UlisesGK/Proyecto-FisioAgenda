package com.fisioagenda.model;

/**
 * Roles de usuario de la aplicación. Cada rol habilita un conjunto distinto de pantallas.
 */
public enum Rol {
    ADMIN("Administrador"),
    FISIOTERAPEUTA("Fisioterapeuta"),
    RECEPCION("Recepción");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
