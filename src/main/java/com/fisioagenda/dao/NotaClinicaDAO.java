package com.fisioagenda.dao;

import com.fisioagenda.model.NotaClinica;

import java.util.List;

/** Operaciones de persistencia sobre la tabla nota_clinica (historial clínico). */
public interface NotaClinicaDAO {
    /** Notas del paciente, de la más reciente a la más antigua, con el autor cargado. */
    List<NotaClinica> listarPorPaciente(int pacienteId);
    NotaClinica insertar(NotaClinica nota);
}
