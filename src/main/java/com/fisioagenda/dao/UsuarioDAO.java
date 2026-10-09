package com.fisioagenda.dao;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;

import java.util.List;
import java.util.Optional;

/** Operaciones de persistencia sobre la tabla usuario. */
public interface UsuarioDAO {
    Optional<Usuario> buscarPorUsername(String username);
    Optional<Usuario> buscarPorId(int id);
    List<Usuario> listarTodos();
    List<Usuario> listarPorRol(Rol rol);
    /** Inserta el usuario, asigna el id generado al objeto y lo devuelve. */
    Usuario insertar(Usuario usuario);
    void actualizar(Usuario usuario);
    /** Actualiza solo los campos de control de acceso (intentos fallidos y bloqueo). */
    void actualizarControlAcceso(Usuario usuario);
}
