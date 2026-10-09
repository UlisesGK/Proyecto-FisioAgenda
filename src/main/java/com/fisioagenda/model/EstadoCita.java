package com.fisioagenda.model;

/**
 * Ciclo de vida de una cita.
 */
public enum EstadoCita {
    PROGRAMADA("Programada"),
    REALIZADA("Realizada"),
    CANCELADA("Cancelada"),
    NO_ASISTIDA("No asistida");

    private final String etiqueta;

    EstadoCita(String etiqueta) {
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
