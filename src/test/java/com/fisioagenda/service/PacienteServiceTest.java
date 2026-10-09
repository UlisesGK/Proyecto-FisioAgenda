package com.fisioagenda.service;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacienteServiceTest {

    private static final LocalDate HOY = LocalDate.of(2030, 1, 7);

    @TempDir
    Path carpeta;

    private SqlitePacienteDAO pacienteDAO;
    private PacienteService servicio;
    private Usuario admin;
    private Usuario fisio;
    private Usuario recepcion;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        SqliteUsuarioDAO usuarioDAO = new SqliteUsuarioDAO(database);
        pacienteDAO = new SqlitePacienteDAO(database);
        servicio = new PacienteService(pacienteDAO, BdPruebas.reloj(HOY.atTime(9, 0)));
        admin = BdPruebas.usuario(usuarioDAO, "admin", Rol.ADMIN);
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        recepcion = BdPruebas.usuario(usuarioDAO, "recepcion", Rol.RECEPCION);
        Sesion.getInstance().iniciar(recepcion);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void altaNormalizaDatosYAsignaFechaDeAltaHoy() {
        Paciente p = paciente(" 12345678-z ", "  Lucía ", "Fernández Gil", "612 34 57 81", "  ");
        Paciente guardado = servicio.guardar(p);

        assertTrue(guardado.getId() > 0);
        assertEquals("12345678Z", guardado.getDni());
        assertEquals("Lucía", guardado.getNombre());
        assertEquals("+34 612345781", guardado.getTelefono());
        assertNull(guardado.getEmail(), "Email vacío se guarda como nulo");
        assertEquals(HOY, guardado.getFechaAlta());
        assertEquals(1, servicio.listar().size());
    }

    @ParameterizedTest(name = "[{index}] {5}")
    @CsvSource(delimiter = '|', value = {
        "1234567A  | Lucía | Fernández | 612345678 | ''                  | DNI/NIE",
        "''        | Lucía | Fernández | 612345678 | ''                  | DNI/NIE",
        "12345678Z | ''    | Fernández | 612345678 | ''                  | nombre",
        "12345678Z | Lucía | '  '      | 612345678 | ''                  | apellidos",
        "12345678Z | Lucía | Fernández | 512345678 | ''                  | teléfono",
        "12345678Z | Lucía | Fernández | 61234567  | ''                  | teléfono",
        "12345678Z | Lucía | Fernández | 612345678 | lucia@              | email",
        "12345678Z | Lucía | Fernández | 612345678 | lucia@example       | email"
    })
    void validacionesDeDatos(String dni, String nombre, String apellidos, String telefono, String email,
                             String campo) {
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(paciente(dni, nombre, apellidos, telefono, email)));
        assertTrue(e.getMessage().contains(campo), "Se esperaba un error sobre " + campo + ": " + e.getMessage());
        assertEquals(0, pacienteDAO.contar());
    }

    @Test
    void laFechaDeNacimientoNoPuedeSerFutura() {
        Paciente p = paciente("12345678Z", "Lucía", "Fernández", "612345678", null);
        p.setFechaNacimiento(HOY.plusDays(1));
        ServiceException e = assertThrows(ServiceException.class, () -> servicio.guardar(p));
        assertTrue(e.getMessage().contains("futura"));
        p.setFechaNacimiento(HOY);
        assertDoesNotThrow(() -> servicio.guardar(p));
    }

    @Test
    void dniDuplicadoSeRechaza() {
        servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null));
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(paciente("12345678z", "Otra", "Persona", "699999999", null)));
        assertTrue(e.getMessage().contains("Ya existe"), e.getMessage());
    }

    @Test
    void editarConservaLaFechaDeAltaYPermiteMantenerElDni() {
        Paciente p = servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null));
        Sesion.getInstance().iniciar(fisio);
        p.setTelefono("699888777");
        p.setFechaAlta(LocalDate.of(2000, 1, 1));
        servicio.guardar(p);

        Paciente leido = servicio.buscarPorId(p.getId()).orElseThrow();
        assertEquals("+34 699888777", leido.getTelefono());
        assertEquals(HOY, leido.getFechaAlta());
    }

    @Test
    void fisioterapeutaNoPuedeDarDeAltaPacientes() {
        Sesion.getInstance().iniciar(fisio);
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null)));
        assertTrue(e.getMessage().startsWith("No tiene permisos para"));
    }

    @Test
    void recepcionNoPuedeEliminarPacientes() {
        Paciente p = servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null));
        ServiceException e = assertThrows(ServiceException.class, () -> servicio.eliminar(p.getId()));
        assertEquals("No tiene permisos para eliminar pacientes.", e.getMessage());
        Sesion.getInstance().iniciar(fisio);
        assertThrows(ServiceException.class, () -> servicio.eliminar(p.getId()));
        assertEquals(1, pacienteDAO.contar());
    }

    @Test
    void adminEliminaPacientes() {
        Paciente p = servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null));
        Sesion.getInstance().iniciar(admin);
        servicio.eliminar(p.getId());
        assertTrue(servicio.buscarPorId(p.getId()).isEmpty());
        assertThrows(ServiceException.class, () -> servicio.eliminar(p.getId()));
    }

    @Test
    void buscarConTextoVacioDevuelveTodos() {
        servicio.guardar(paciente("12345678Z", "Lucía", "Fernández Gil", "612345678", null));
        servicio.guardar(paciente("11111111H", "Javier", "Romero Navarro", "634128957", "javier@example.com"));
        assertEquals(2, servicio.buscar("").size());
        assertEquals(2, servicio.buscar(null).size());
        assertEquals(1, servicio.buscar("romero").size());
        assertEquals(1, servicio.buscar(" LUCIA  fernandez ").size(), "Sin tildes ni mayúsculas, palabras sueltas");
    }

    private static Paciente paciente(String dni, String nombre, String apellidos, String telefono, String email) {
        Paciente p = new Paciente();
        p.setDni(dni);
        p.setNombre(nombre);
        p.setApellidos(apellidos);
        p.setTelefono(telefono);
        p.setEmail(email);
        p.setFechaNacimiento(LocalDate.of(1985, 3, 14));
        return p;
    }

    @Test
    void elDniSeGuardaAunqueLaLetraNoCuadreSiTieneFormatoEspanol() {
        Paciente guardado = servicio.guardar(paciente("12.345.678-a", "Lucía", "Fernández", "612345678", null));
        assertEquals("12345678A", guardado.getDni());
    }
}
