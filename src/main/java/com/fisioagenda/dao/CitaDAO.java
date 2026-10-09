package com.fisioagenda.dao;

import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Operaciones de persistencia sobre la tabla cita. Las citas se devuelven con su paciente,
 * fisioterapeuta y tratamiento ya cargados (una sola consulta con JOIN).
 */
public interface CitaDAO {
    Optional<Cita> buscarPorId(int id);
    /** Citas de un día, ordenadas por hora. Si fisioterapeutaId es null se devuelven las de todos. */
    List<Cita> listarPorDia(LocalDate dia, Integer fisioterapeutaId);
    /** Citas en el intervalo [desde, hasta), ordenadas por fecha. */
    List<Cita> listarEntre(LocalDateTime desde, LocalDateTime hasta, Integer fisioterapeutaId);
    List<Cita> listarPorPaciente(int pacienteId);
    Cita insertar(Cita cita);
    void actualizar(Cita cita);
    void actualizarEstado(int citaId, EstadoCita estado);
    void eliminar(int id);
    int contarPorEstadoEntre(EstadoCita estado, LocalDateTime desde, LocalDateTime hasta);
}
