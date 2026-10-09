package com.fisioagenda.dao;

import com.fisioagenda.model.Tratamiento;

import java.util.List;
import java.util.Optional;

/** Operaciones de persistencia sobre la tabla tratamiento. */
public interface TratamientoDAO {
    Optional<Tratamiento> buscarPorId(int id);
    List<Tratamiento> listarTodos();
    List<Tratamiento> listarActivos();
    Tratamiento insertar(Tratamiento tratamiento);
    void actualizar(Tratamiento tratamiento);
}
