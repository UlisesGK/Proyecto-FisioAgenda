package com.fisioagenda.service;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqliteCitaDAO;
import com.fisioagenda.dao.sqlite.SqliteNotaClinicaDAO;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteTratamientoDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static com.fisioagenda.BdPruebas.LUNES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorialServiceTest {

    private static final LocalDateTime AHORA = LUNES.atTime(11, 7, 33);

    @TempDir
    Path carpeta;

    private HistorialService servicio;
    private Usuario admin;
    private Usuario fisio;
    private Usuario recepcion;
    private Paciente lucia;
    private Paciente javier;
    private Cita citaDeLucia;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        SqliteUsuarioDAO usuarioDAO = new SqliteUsuarioDAO(database);
        SqlitePacienteDAO pacienteDAO = new SqlitePacienteDAO(database);
        SqliteCitaDAO citaDAO = new SqliteCitaDAO(database);
        servicio = new HistorialService(new SqliteNotaClinicaDAO(database), pacienteDAO, citaDAO, BdPruebas.reloj(AHORA));

        admin = BdPruebas.usuario(usuarioDAO, "admin", Rol.ADMIN);
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        recepcion = BdPruebas.usuario(usuarioDAO, "recepcion", Rol.RECEPCION);
        lucia = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        javier = BdPruebas.paciente(pacienteDAO, "11111111H", "Javier", "Romero Navarro");
        Tratamiento general = BdPruebas.tratamiento(new SqliteTratamientoDAO(database), "General", 45, 35);
        citaDeLucia = citaDAO.insertar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void fisioterapeutaAnadeNotaComoAutorConFechaActual() {
        Sesion.getInstance().iniciar(fisio);
        NotaClinica nota = servicio.anadirNota(lucia.getId(), citaDeLucia.getId(), "  Mejora de la movilidad lumbar.  ", 4);

        assertTrue(nota.getId() > 0);
        assertEquals(fisio.getId(), nota.getAutor().getId());
        assertEquals(LUNES.atTime(11, 7), nota.getFecha());
        assertEquals("Mejora de la movilidad lumbar.", nota.getContenido());

        List<NotaClinica> notas = servicio.notasDe(lucia.getId());
        assertEquals(1, notas.size());
        assertEquals(4, notas.get(0).getEscalaDolor());
        assertEquals(citaDeLucia.getId(), notas.get(0).getCitaId());
        assertEquals("Nombre de fisio", notas.get(0).getAutor().getNombreCompleto());
    }

    @Test
    void recepcionNoPuedeVerNiAnadirNotas() {
        Sesion.getInstance().iniciar(recepcion);
        ServiceException ver = assertThrows(ServiceException.class, () -> servicio.notasDe(lucia.getId()));
        assertEquals("No tiene permisos para consultar el historial clínico.", ver.getMessage());
        assertThrows(ServiceException.class, () -> servicio.anadirNota(lucia.getId(), null, "Nota de prueba", 3));
    }

    @Test
    void adminPuedeVerPeroNoAnadirNotas() {
        Sesion.getInstance().iniciar(fisio);
        servicio.anadirNota(lucia.getId(), null, "Nota general del paciente", 5);
        Sesion.getInstance().iniciar(admin);
        assertEquals(1, servicio.notasDe(lucia.getId()).size());
        assertThrows(ServiceException.class, () -> servicio.anadirNota(lucia.getId(), null, "Nota del admin", 5));
    }

    @ParameterizedTest(name = "contenido de {0} caracteres no válido")
    @ValueSource(ints = {0, 4, 2001})
    void contenidoEntre5Y2000Caracteres(int longitud) {
        Sesion.getInstance().iniciar(fisio);
        String contenido = "x".repeat(longitud);
        assertThrows(ServiceException.class, () -> servicio.anadirNota(lucia.getId(), null, contenido, 3));
    }

    @ParameterizedTest(name = "EVA {0} fuera de rango")
    @ValueSource(ints = {-1, 11})
    void escalaDeDolorEntre0Y10(int eva) {
        Sesion.getInstance().iniciar(fisio);
        assertThrows(ServiceException.class, () -> servicio.anadirNota(lucia.getId(), null, "Nota válida", eva));
    }

    @Test
    void laCitaDebePertenecerAlPaciente() {
        Sesion.getInstance().iniciar(fisio);
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.anadirNota(javier.getId(), citaDeLucia.getId(), "Nota de otra cita", 3));
        assertTrue(e.getMessage().contains("no pertenece"), e.getMessage());
        assertThrows(ServiceException.class, () -> servicio.anadirNota(9999, null, "Paciente inexistente", 3));
    }
}
