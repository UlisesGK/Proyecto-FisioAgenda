package com.fisioagenda.service;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.Sesion;

/**
 * Comprobaciones de autenticación y rol compartidas por los servicios. El control de acceso se hace
 * aquí, en la capa de servicio, y no solo ocultando botones en la interfaz.
 */
final class Permisos {

    private Permisos() {
    }

    /**
     * Devuelve el usuario de la sesión.
     *
     * @throws ServiceException si no hay sesión iniciada
     */
    static Usuario exigirSesion() {
        Usuario usuario = Sesion.getInstance().getUsuario();
        if (usuario == null) {
            throw new ServiceException("Debe iniciar sesión para realizar esta operación.");
        }
        return usuario;
    }

    /**
     * Comprueba que el usuario de la sesión tenga alguno de los roles indicados.
     *
     * @param accion texto que completa el mensaje "No tiene permisos para ..." (p. ej. "eliminar pacientes")
     * @return el usuario de la sesión
     * @throws ServiceException si no hay sesión o el rol no está permitido
     */
    static Usuario exigirRol(String accion, Rol... roles) {
        Usuario usuario = exigirSesion();
        for (Rol rol : roles) {
            if (usuario.getRol() == rol) {
                return usuario;
            }
        }
        throw new ServiceException("No tiene permisos para " + accion + ".");
    }

    /** Id del usuario de la sesión si es fisioterapeuta; {@code null} en otro caso (ver datos de todos). */
    static Integer fisioterapeutaDeLaSesion() {
        Usuario usuario = exigirSesion();
        return usuario.getRol() == Rol.FISIOTERAPEUTA ? usuario.getId() : null;
    }
}
