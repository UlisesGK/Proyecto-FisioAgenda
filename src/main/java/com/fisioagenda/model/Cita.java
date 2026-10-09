package com.fisioagenda.model;

import java.time.LocalDateTime;

/**
 * Cita que relaciona a un paciente, un fisioterapeuta y un tratamiento en una franja horaria.
 * Los objetos relacionados se cargan junto a la cita para poder mostrarlos sin consultas extra.
 */
public class Cita {
    private int id;
    private Paciente paciente;
    private Usuario fisioterapeuta;
    private Tratamiento tratamiento;
    private LocalDateTime fechaHora;
    private EstadoCita estado = EstadoCita.PROGRAMADA;
    private String notas;

    public Cita() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
    public Usuario getFisioterapeuta() { return fisioterapeuta; }
    public void setFisioterapeuta(Usuario fisioterapeuta) { this.fisioterapeuta = fisioterapeuta; }
    public Tratamiento getTratamiento() { return tratamiento; }
    public void setTratamiento(Tratamiento tratamiento) { this.tratamiento = tratamiento; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public EstadoCita getEstado() { return estado; }
    public void setEstado(EstadoCita estado) { this.estado = estado; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    /** Hora a la que termina la cita según la duración del tratamiento. */
    public LocalDateTime getFechaHoraFin() {
        return fechaHora.plusMinutes(tratamiento.getDuracionMinutos());
    }

    /** Indica si esta cita se solapa en el tiempo con otra. */
    public boolean seSolapaCon(Cita otra) {
        return fechaHora.isBefore(otra.getFechaHoraFin()) && otra.getFechaHora().isBefore(getFechaHoraFin());
    }
}
