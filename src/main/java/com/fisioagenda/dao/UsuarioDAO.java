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

    /** Guarda el hash del token de un dispositivo recordado y borra los que ya han caducado. */
    void guardarTokenDispositivo(int usuarioId, String tokenHash, java.time.LocalDateTime ahora,
                                 java.time.LocalDateTime expira);

    /** Id del usuario al que pertenece un token de dispositivo que aún no ha caducado. */
    Optional<Integer> buscarUsuarioPorToken(String tokenHash, java.time.LocalDateTime ahora);

    /** Borra un token de dispositivo recordado. */
    void borrarTokenDispositivo(String tokenHash);

    /** Borra todos los dispositivos recordados de un usuario (por ejemplo, al cambiar su contraseña). */
    void borrarTokensDe(int usuarioId);
}
