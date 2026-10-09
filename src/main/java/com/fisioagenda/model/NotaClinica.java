package com.fisioagenda.model;

import java.time.LocalDateTime;

/**
 * Entrada del historial clínico de un paciente, redactada por un fisioterapeuta.
 * Puede asociarse a una cita concreta (sesión) o ser una anotación general.
 */
public class NotaClinica {
    private int id;
    private int pacienteId;
    private Integer citaId;
    private Usuario autor;
    private LocalDateTime fecha;
    private String contenido;
    private int escalaDolor;

    public NotaClinica() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }
    public Integer getCitaId() { return citaId; }
    public void setCitaId(Integer citaId) { this.citaId = citaId; }
    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    /** Dolor percibido por el paciente en la Escala Visual Analógica (0 = sin dolor, 10 = máximo). */
    public int getEscalaDolor() { return escalaDolor; }
    public void setEscalaDolor(int escalaDolor) { this.escalaDolor = escalaDolor; }
}
