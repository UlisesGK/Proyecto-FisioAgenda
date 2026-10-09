package com.fisioagenda.service;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.PasswordHasher;
import com.fisioagenda.security.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2030, 1, 7, 10, 0);

    @TempDir
    Path carpeta;

    private SqliteUsuarioDAO usuarioDAO;
    private AuthService auth;
    private Usuario fisio;

    @BeforeEach
    void setUp() {
        usuarioDAO = new SqliteUsuarioDAO(BdPruebas.nuevaBd(carpeta));
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        auth = new AuthService(usuarioDAO, BdPruebas.reloj(AHORA));
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void loginCorrectoIniciaLaSesionSinExponerCredenciales() {
        Usuario usuario = auth.login("Fisio ", pwd(BdPruebas.PASSWORD));
        assertEquals(fisio.getId(), usuario.getId());
        assertSame(usuario, Sesion.getInstance().getUsuario());
        assertTrue(Sesion.getInstance().tieneRol(Rol.FISIOTERAPEUTA));
        assertNull(usuario.getPasswordHash());
        assertNull(usuario.getSalt());

        auth.logout();
        assertFalse(Sesion.getInstance().estaIniciada());
    }

    @Test
    void loginBorraElArrayDeLaContrasena() {
        char[] password = pwd(BdPruebas.PASSWORD);
        auth.login("fisio", password);
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void contrasenaIncorrectaYUsuarioInexistenteDanElMismoMensaje() {
        ServiceException incorrecta = assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
        ServiceException inexistente = assertThrows(ServiceException.class, () -> auth.login("nadie", pwd("Mala1234!")));
        assertEquals(AuthService.MENSAJE_CREDENCIALES, incorrecta.getMessage());
        assertEquals(incorrecta.getMessage(), inexistente.getMessage());
        assertFalse(Sesion.getInstance().estaIniciada());
        assertEquals(1, usuarioDAO.buscarPorId(fisio.getId()).orElseThrow().getIntentosFallidos());
    }

    @Test
    void bloqueoTrasCincoFallosConRelojFijo() {
        for (int i = 1; i < AuthService.MAX_INTENTOS; i++) {
            ServiceException e = assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
            assertEquals(AuthService.MENSAJE_CREDENCIALES, e.getMessage());
        }
        ServiceException quinto = assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
        assertTrue(quinto.getMessage().contains("bloqueada"), quinto.getMessage());

        Usuario bloqueado = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertEquals(0, bloqueado.getIntentosFallidos(), "El contador vuelve a 0 al bloquear");
        assertEquals(AHORA.plusMinutes(5), bloqueado.getBloqueadoHasta());

        // Bloqueada: ni siquiera con la contraseña correcta
        ServiceException correcta = assertThrows(ServiceException.class,
                () -> auth.login("fisio", pwd(BdPruebas.PASSWORD)));
        assertTrue(correcta.getMessage().contains("5 minutos"), correcta.getMessage());

        // Un segundo antes de que acabe el bloqueo sigue bloqueada
        AuthService casi = new AuthService(usuarioDAO, BdPruebas.reloj(AHORA.plusMinutes(5).minusSeconds(1)));
        ServiceException e = assertThrows(ServiceException.class, () -> casi.login("fisio", pwd(BdPruebas.PASSWORD)));
        assertTrue(e.getMessage().contains("1 minuto."), e.getMessage());
    }

    @Test
    void unUsuarioInexistenteRecibeExactamenteLasMismasRespuestasQueUnoReal() {
        RelojAjustable reloj = new RelojAjustable(AHORA);
        AuthService servicio = new AuthService(usuarioDAO, reloj);
        // Cinco fallos (el quinto bloquea) y un sexto intento ya bloqueado: mismos mensajes con y sin cuenta real
        for (int i = 1; i <= AuthService.MAX_INTENTOS + 1; i++) {
            String real = assertThrows(ServiceException.class,
                    () -> servicio.login("fisio", pwd("Mala1234!"))).getMessage();
            String inventado = assertThrows(ServiceException.class,
                    () -> servicio.login("Nadie", pwd("Mala1234!"))).getMessage();
            assertEquals(real, inventado, "Intento " + i);
            if (i < AuthService.MAX_INTENTOS) {
                assertEquals(AuthService.MENSAJE_CREDENCIALES, real);
            } else {
                assertTrue(real.contains("bloqueada"), real);
            }
        }
        // La contraseña correcta de la cuenta real tampoco la distingue mientras dura el bloqueo
        assertEquals(assertThrows(ServiceException.class, () -> servicio.login("nadie", pwd(BdPruebas.PASSWORD)))
                        .getMessage(),
                assertThrows(ServiceException.class, () -> servicio.login("fisio", pwd(BdPruebas.PASSWORD)))
                        .getMessage());

        // Pasado el bloqueo, ambos vuelven a recibir el mensaje genérico
        reloj.avanzarMinutos(AuthService.MINUTOS_BLOQUEO);
        assertEquals(AuthService.MENSAJE_CREDENCIALES,
                assertThrows(ServiceException.class, () -> servicio.login("nadie", pwd("Mala1234!"))).getMessage());
        assertEquals(AuthService.MENSAJE_CREDENCIALES,
                assertThrows(ServiceException.class, () -> servicio.login("fisio", pwd("Mala1234!"))).getMessage());
        assertDoesNotThrow(() -> servicio.login("fisio", pwd(BdPruebas.PASSWORD)));
    }

    @Test
    void desbloqueoAlPasarCincoMinutos() {
        for (int i = 0; i < AuthService.MAX_INTENTOS; i++) {
            assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
        }
        AuthService despues = new AuthService(usuarioDAO, BdPruebas.reloj(AHORA.plusMinutes(5)));
        assertDoesNotThrow(() -> despues.login("fisio", pwd(BdPruebas.PASSWORD)));

        Usuario desbloqueado = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertNull(desbloqueado.getBloqueadoHasta());
        assertEquals(0, desbloqueado.getIntentosFallidos());
    }

    @Test
    void loginCorrectoReiniciaElContadorDeFallos() {
        assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
        assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!")));
        auth.login("fisio", pwd(BdPruebas.PASSWORD));
        assertEquals(0, usuarioDAO.buscarPorId(fisio.getId()).orElseThrow().getIntentosFallidos());
    }

    @Test
    void usuarioInactivoNoPuedeEntrar() {
        Usuario inactivo = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        inactivo.setActivo(false);
        usuarioDAO.actualizar(inactivo);

        ServiceException e = assertThrows(ServiceException.class, () -> auth.login("fisio", pwd(BdPruebas.PASSWORD)));
        assertTrue(e.getMessage().contains("desactivada"), e.getMessage());
        assertFalse(Sesion.getInstance().estaIniciada());
    }

    @Test
    void laCuentaDesactivadaSoloSeRevelaConLaContrasenaCorrecta() {
        Usuario inactivo = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        inactivo.setActivo(false);
        usuarioDAO.actualizar(inactivo);
        assertEquals(AuthService.MENSAJE_CREDENCIALES,
                assertThrows(ServiceException.class, () -> auth.login("fisio", pwd("Mala1234!"))).getMessage());
    }

    @Test
    void loginSinDatosSeRechaza() {
        assertThrows(ServiceException.class, () -> auth.login("  ", pwd(BdPruebas.PASSWORD)));
        assertThrows(ServiceException.class, () -> auth.login("fisio", new char[0]));
    }

    @Test
    void cambiarPasswordDelUsuarioDeLaSesion() {
        auth.login("fisio", pwd(BdPruebas.PASSWORD));
        auth.cambiarPassword(pwd(BdPruebas.PASSWORD), pwd("NuevaClave9#"), pwd("NuevaClave9#"));

        Usuario guardado = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertTrue(PasswordHasher.verificar(pwd("NuevaClave9#"), guardado.getSalt(), guardado.getPasswordHash()));
        auth.logout();
        assertDoesNotThrow(() -> auth.login("fisio", pwd("NuevaClave9#")));
    }

    @Test
    void cambiarPasswordValidaActualRepeticionYPolitica() {
        auth.login("fisio", pwd(BdPruebas.PASSWORD));
        ServiceException actual = assertThrows(ServiceException.class,
                () -> auth.cambiarPassword(pwd("Otra1234!"), pwd("NuevaClave9#"), pwd("NuevaClave9#")));
        assertTrue(actual.getMessage().contains("actual"));
        ServiceException repetida = assertThrows(ServiceException.class,
                () -> auth.cambiarPassword(pwd(BdPruebas.PASSWORD), pwd("NuevaClave9#"), pwd("NuevaClave9$")));
        assertTrue(repetida.getMessage().contains("no coinciden"));
        ServiceException debil = assertThrows(ServiceException.class,
                () -> auth.cambiarPassword(pwd(BdPruebas.PASSWORD), pwd("debil"), pwd("debil")));
        assertTrue(debil.getMessage().contains("8 caracteres"));
    }

    @Test
    void cambiarPasswordExigeSesion() {
        assertThrows(ServiceException.class,
                () -> auth.cambiarPassword(pwd(BdPruebas.PASSWORD), pwd("NuevaClave9#"), pwd("NuevaClave9#")));
    }

    private static char[] pwd(String texto) {
        return texto.toCharArray();
    }

    /** Reloj de prueba que se puede adelantar (el contador de nombres inexistentes vive en el propio servicio). */
    private static final class RelojAjustable extends Clock {
        private final ZoneId zona = ZoneId.systemDefault();
        private Instant instante;

        RelojAjustable(LocalDateTime ahora) {
            instante = ahora.atZone(zona).toInstant();
        }

        void avanzarMinutos(long minutos) {
            instante = instante.plusSeconds(minutos * 60);
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId otraZona) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
