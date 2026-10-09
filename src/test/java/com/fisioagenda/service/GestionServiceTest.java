package com.fisioagenda.service;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqliteCitaDAO;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteTratamientoDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.PasswordHasher;
import com.fisioagenda.security.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static com.fisioagenda.BdPruebas.LUNES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de los servicios de administración: tratamientos y usuarios. */
class GestionServiceTest {

    /** "Ahora" en las pruebas: lunes 7 de enero de 2030 a las 12:00. */
    private static final LocalDateTime AHORA = LUNES.atTime(12, 0);

    @TempDir
    Path carpeta;

    private SqliteUsuarioDAO usuarioDAO;
    private SqliteTratamientoDAO tratamientoDAO;
    private SqliteCitaDAO citaDAO;
    private SqlitePacienteDAO pacienteDAO;
    private TratamientoService tratamientos;
    private UsuarioService usuarios;
    private Usuario admin;
    private Usuario fisio;
    private Usuario recepcion;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        usuarioDAO = new SqliteUsuarioDAO(database);
        tratamientoDAO = new SqliteTratamientoDAO(database);
        citaDAO = new SqliteCitaDAO(database);
        pacienteDAO = new SqlitePacienteDAO(database);
        tratamientos = new TratamientoService(tratamientoDAO, citaDAO, BdPruebas.reloj(AHORA));
        usuarios = new UsuarioService(usuarioDAO);
        admin = BdPruebas.usuario(usuarioDAO, "admin", Rol.ADMIN);
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        recepcion = BdPruebas.usuario(usuarioDAO, "recepcion", Rol.RECEPCION);
        Sesion.getInstance().iniciar(admin);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    // ---------------------------------------------------------------- tratamientos

    @Test
    void adminCreaYDesactivaTratamientos() {
        Tratamiento t = tratamientos.guardar(new Tratamiento(0, " Kinesiotaping ", 20, 15));
        assertTrue(t.getId() > 0);
        assertEquals("Kinesiotaping", t.getNombre());
        t.setActivo(false);
        tratamientos.guardar(t);
        assertEquals(1, tratamientos.listar().size());
        assertTrue(tratamientos.listarActivos().isEmpty());
    }

    @ParameterizedTest(name = "nombre=\"{0}\", duración={1}, precio={2}")
    @CsvSource({"'', 30, 20", "Masaje, 9, 20", "Masaje, 241, 20", "Masaje, 30, -0.01"})
    void validacionesDeTratamiento(String nombre, int duracion, double precio) {
        assertThrows(ServiceException.class, () -> tratamientos.guardar(new Tratamiento(0, nombre, duracion, precio)));
    }

    @Test
    void nombreDeTratamientoUnicoSinDistinguirMayusculas() {
        tratamientos.guardar(new Tratamiento(0, "Punción seca", 30, 40));
        assertThrows(ServiceException.class, () -> tratamientos.guardar(new Tratamiento(0, "PUNCIÓN SECA", 30, 40)));
    }

    @Test
    void soloAdminGestionaTratamientosPeroTodosLosVen() {
        tratamientos.guardar(new Tratamiento(0, "Electroterapia", 20, 20));
        for (Usuario u : new Usuario[]{fisio, recepcion}) {
            Sesion.getInstance().iniciar(u);
            assertEquals(1, tratamientos.listar().size());
            assertThrows(ServiceException.class, () -> tratamientos.guardar(new Tratamiento(0, "Otro", 20, 20)));
        }
    }

    @Test
    void laDuracionNoCambiaSiHayCitasProgramadasDeHoyEnAdelante() {
        Tratamiento t = tratamientos.guardar(new Tratamiento(0, "Fisioterapia general", 45, 35));
        // Una de hoy ya pasada (aún sin marcar) y otra futura: ambas cambiarían de hora de fin
        citaProgramada(t, AHORA.minusHours(3));
        citaProgramada(t, LUNES.plusDays(2).atTime(10, 0));

        Tratamiento cambio = new Tratamiento(t.getId(), "Fisioterapia general", 60, 35);
        ServiceException e = assertThrows(ServiceException.class, () -> tratamientos.guardar(cambio));
        assertTrue(e.getMessage().contains("duración") && e.getMessage().contains("2 citas programadas"),
                e.getMessage());
        assertEquals(45, tratamientoDAO.buscarPorId(t.getId()).orElseThrow().getDuracionMinutos());

        // El resto de datos sí se pueden cambiar
        Tratamiento otrosDatos = new Tratamiento(t.getId(), "Fisioterapia general (sesión)", 45, 38.5);
        assertDoesNotThrow(() -> tratamientos.guardar(otrosDatos));
        assertEquals(38.5, tratamientoDAO.buscarPorId(t.getId()).orElseThrow().getPrecio());
    }

    @Test
    void laDuracionSePuedeCambiarSiSoloHayCitasPasadasOCanceladas() {
        Tratamiento t = tratamientos.guardar(new Tratamiento(0, "Masaje", 30, 30));
        Tratamiento otro = tratamientos.guardar(new Tratamiento(0, "Punción seca", 30, 40));
        citaProgramada(t, LUNES.minusDays(2).atTime(10, 0));                        // día pasado
        Cita cancelada = citaProgramada(t, LUNES.plusDays(1).atTime(10, 0));
        citaDAO.actualizarEstado(cancelada.getId(), EstadoCita.CANCELADA);
        Cita realizada = citaProgramada(t, AHORA.minusHours(2));
        citaDAO.actualizarEstado(realizada.getId(), EstadoCita.REALIZADA);
        citaProgramada(otro, LUNES.plusDays(1).atTime(12, 0));                      // de otro tratamiento

        Tratamiento cambio = new Tratamiento(t.getId(), "Masaje", 45, 30);
        assertDoesNotThrow(() -> tratamientos.guardar(cambio));
        assertEquals(45, tratamientoDAO.buscarPorId(t.getId()).orElseThrow().getDuracionMinutos());
    }

    /** Inserta directamente (sin las reglas de CitaService) una cita programada del tratamiento indicado. */
    private Cita citaProgramada(Tratamiento t, LocalDateTime fechaHora) {
        Paciente p = pacienteDAO.listarTodos().stream().findFirst()
                .orElseGet(() -> BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil"));
        return citaDAO.insertar(BdPruebas.cita(p, fisio, t, fechaHora));
    }

    // ---------------------------------------------------------------- usuarios

    @Test
    void crearUsuarioConPasswordSeguraYSinExponerCredenciales() {
        char[] password = "Segura123!".toCharArray();
        Usuario nuevo = usuarios.crear(new Usuario(0, " Marta.S ", "Marta Sánchez López", Rol.FISIOTERAPEUTA), password);

        assertTrue(nuevo.getId() > 0);
        assertEquals("marta.s", nuevo.getUsername());
        assertNull(nuevo.getPasswordHash());
        assertArrayEquals(new char[password.length], password, "La contraseña se borra de memoria");
        Usuario guardado = usuarioDAO.buscarPorUsername("marta.s").orElseThrow();
        assertTrue(PasswordHasher.verificar("Segura123!".toCharArray(), guardado.getSalt(), guardado.getPasswordHash()));
        assertEquals(2, usuarios.listarFisioterapeutas().size());
        assertTrue(usuarios.listar().stream().allMatch(u -> u.getPasswordHash() == null));
    }

    @Test
    void crearUsuarioValidaUsernameUnicidadYPassword() {
        assertThrows(ServiceException.class,
                () -> usuarios.crear(new Usuario(0, "ab", "Nombre", Rol.RECEPCION), "Segura123!".toCharArray()));
        assertThrows(ServiceException.class,
                () -> usuarios.crear(new Usuario(0, "fisio", "Nombre", Rol.RECEPCION), "Segura123!".toCharArray()));
        assertThrows(ServiceException.class,
                () -> usuarios.crear(new Usuario(0, "nuevo", "Nombre", Rol.RECEPCION), "debil".toCharArray()));
        assertThrows(ServiceException.class,
                () -> usuarios.crear(new Usuario(0, "nuevo", " ", Rol.RECEPCION), "Segura123!".toCharArray()));
    }

    @Test
    void soloAdminGestionaUsuarios() {
        Sesion.getInstance().iniciar(recepcion);
        ServiceException e = assertThrows(ServiceException.class, () -> usuarios.listar());
        assertEquals("No tiene permisos para gestionar usuarios.", e.getMessage());
        assertThrows(ServiceException.class, () -> usuarios.desbloquear(fisio.getId()));
        assertEquals(1, usuarios.listarFisioterapeutas().size(), "Cualquier rol puede listar fisioterapeutas");
    }

    @Test
    void actualizarUsuarioYNoDesactivarseASiMismo() {
        Usuario cambios = new Usuario(fisio.getId(), "fisio", "Carlos Pérez Gómez", Rol.FISIOTERAPEUTA);
        cambios.setActivo(false);
        usuarios.actualizar(cambios);
        Usuario leido = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertEquals("Carlos Pérez Gómez", leido.getNombreCompleto());
        assertFalse(leido.isActivo());
        assertTrue(usuarios.listarFisioterapeutas().isEmpty(), "Los inactivos no aparecen en la agenda");
        assertTrue(leido.getPasswordHash() != null, "Actualizar no borra la contraseña");

        Usuario yoMismo = new Usuario(admin.getId(), "admin", "Administración", Rol.ADMIN);
        yoMismo.setActivo(false);
        assertThrows(ServiceException.class, () -> usuarios.actualizar(yoMismo));
        Usuario sinRol = new Usuario(admin.getId(), "admin", "Administración", Rol.RECEPCION);
        assertThrows(ServiceException.class, () -> usuarios.actualizar(sinRol));
    }

    @Test
    void restablecerPasswordYDesbloquear() {
        Usuario bloqueado = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        bloqueado.setIntentosFallidos(3);
        bloqueado.setBloqueadoHasta(LocalDateTime.of(2099, 1, 1, 0, 0));
        usuarioDAO.actualizarControlAcceso(bloqueado);

        usuarios.desbloquear(fisio.getId());
        Usuario leido = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertEquals(0, leido.getIntentosFallidos());
        assertNull(leido.getBloqueadoHasta());

        usuarios.restablecerPassword(fisio.getId(), "Restablecida1#".toCharArray());
        leido = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertTrue(PasswordHasher.verificar("Restablecida1#".toCharArray(), leido.getSalt(), leido.getPasswordHash()));
        assertThrows(ServiceException.class, () -> usuarios.restablecerPassword(fisio.getId(), "corta".toCharArray()));
    }
}
