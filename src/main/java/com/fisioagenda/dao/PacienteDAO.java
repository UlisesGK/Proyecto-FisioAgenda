package com.fisioagenda.dao;

import com.fisioagenda.model.Paciente;

import java.util.List;
import java.util.Optional;

/** Operaciones de persistencia sobre la tabla paciente. */
public interface PacienteDAO {
    Optional<Paciente> buscarPorId(int id);
    Optional<Paciente> buscarPorDni(String dni);
    List<Paciente> listarTodos();
    /** Búsqueda por nombre, apellidos, DNI o teléfono (contiene, sin distinguir mayúsculas). */
    List<Paciente> buscar(String texto);
    Paciente insertar(Paciente paciente);
    void actualizar(Paciente paciente);
    void eliminar(int id);
    int contar();
}
