package com.fisioagenda.security;

import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesionTest {

    private final Sesion sesion = Sesion.getInstance();

    @AfterEach
    void tearDown() {
        sesion.cerrar();
    }

    @Test
    void iniciarYCerrarSesion() {
        Usuario usuario = new Usuario(7, "fisio", "Carlos Pérez", Rol.FISIOTERAPEUTA);
        sesion.iniciar(usuario);
        assertTrue(sesion.estaIniciada());
        assertSame(usuario, sesion.getUsuario());
        assertTrue(sesion.tieneRol(Rol.ADMIN, Rol.FISIOTERAPEUTA));
        assertFalse(sesion.tieneRol(Rol.RECEPCION));

        sesion.cerrar();
        assertFalse(sesion.estaIniciada());
        assertNull(sesion.getUsuario());
        assertFalse(sesion.tieneRol(Rol.FISIOTERAPEUTA));
    }
}
