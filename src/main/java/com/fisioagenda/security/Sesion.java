package com.fisioagenda.security;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;

import java.util.Objects;

/**
 * Sesión del usuario autenticado (singleton). La inicia {@code AuthService.login} y la consultan
 * los servicios para aplicar el control de acceso por rol.
 * <p>
 * El cierre automático por inactividad lo gestiona la interfaz llamando a {@link #cerrar()}.
 */
public final class Sesion {

    private static final Sesion INSTANCIA = new Sesion();

    private volatile Usuario usuario;

    private Sesion() {
    }

    /** Devuelve la sesión única de la aplicación. */
    public static Sesion getInstance() {
        return INSTANCIA;
    }

    /** Inicia la sesión con el usuario indicado (sustituye a cualquier sesión anterior). */
    public void iniciar(Usuario usuario) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
    }

    /** Cierra la sesión actual, si la hay. */
    public void cerrar() {
        usuario = null;
    }

    /** Usuario autenticado, o {@code null} si no hay sesión iniciada. */
    public Usuario getUsuario() {
        return usuario;
    }

    /** Indica si hay un usuario autenticado. */
    public boolean estaIniciada() {
        return usuario != null;
    }

    /**
     * Indica si el usuario de la sesión tiene alguno de los roles indicados.
     *
     * @return {@code false} si no hay sesión iniciada
     */
    public boolean tieneRol(Rol... roles) {
        Usuario actual = usuario;
        if (actual == null || roles == null) {
            return false;
        }
        for (Rol rol : roles) {
            if (actual.getRol() == rol) {
                return true;
            }
        }
        return false;
    }
}
