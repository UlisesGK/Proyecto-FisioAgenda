package com.fisioagenda.db;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqliteCitaDAO;
import com.fisioagenda.dao.sqlite.SqliteNotaClinicaDAO;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteTratamientoDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.Sesion;
import com.fisioagenda.service.AuthService;
import com.fisioagenda.service.CitaService;
import com.fisioagenda.util.Validador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del esquema y de los datos de demostración. */
class EsquemaBDTest {

    private static final LocalTime INICIO_DEMO = LocalTime.of(9, 0);
    private static final LocalTime FIN_DEMO = LocalTime.of(20, 0);

    @TempDir
    Path carpeta;

    private SqliteUsuarioDAO usuarioDAO;
    private SqlitePacienteDAO pacienteDAO;
    private SqliteTratamientoDAO tratamientoDAO;
    private SqliteCitaDAO citaDAO;
    private SqliteNotaClinicaDAO notaDAO;

    @BeforeEach
    void setUp() {
        Database.configurar(carpeta.resolve("demo.db").toString());
        Database database = Database.getInstance();
        usuarioDAO = new SqliteUsuarioDAO(database);
        pacienteDAO = new SqlitePacienteDAO(database);
        tratamientoDAO = new SqliteTratamientoDAO(database);
        citaDAO = new SqliteCitaDAO(database);
        notaDAO = new SqliteNotaClinicaDAO(database);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    /** Momentos de distintos días y horas: domingo, madrugada, en mitad de la jornada, noche, sábado... */
    static Stream<LocalDateTime> instantes() {
        return Stream.of(
                LocalDateTime.of(2026, 10, 11, 10, 0),     // domingo: las citas "de hoy" pasan al lunes
                LocalDateTime.of(2026, 10, 12, 0, 0),      // lunes a medianoche
                LocalDateTime.of(2026, 10, 12, 7, 0),      // lunes antes de abrir
                LocalDateTime.of(2026, 10, 8, 10, 20),     // jueves con citas en curso
                LocalDateTime.of(2026, 10, 14, 13, 37),    // miércoles a mediodía
                LocalDateTime.of(2026, 10, 16, 23, 30),    // viernes por la noche: todo lo de hoy ya pasó
                LocalDateTime.of(2026, 10, 17, 19, 15),    // sábado por la tarde
                LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
    }

    @ParameterizedTest(name = "ahora = {0}")
    @MethodSource("instantes")
    void losDatosDeDemostracionCumplenLasReglasDeCitaService(LocalDateTime ahora) {
        Clock reloj = BdPruebas.reloj(ahora);
        EsquemaBD.inicializar(reloj);
        LocalDate hoy = ahora.toLocalDate();
        LocalDate referencia = EsquemaBD.diaDeReferencia(hoy);

        List<Cita> citas = citaDAO.listarEntre(ahora.minusDays(60), ahora.plusDays(60), null);
        assertTrue(citas.size() >= 20 && citas.size() <= 35, "~25 citas: " + citas.size());

        Map<Integer, LocalDate> primeraCitaPorPaciente = new HashMap<>();
        for (Cita c : citas) {
            String id = "Cita " + c.getFechaHora() + " de " + c.getPaciente().getNombreCompleto();
            assertNotEquals(DayOfWeek.SUNDAY, c.getFechaHora().getDayOfWeek(), id);
            assertFalse(c.getFechaHora().toLocalTime().isBefore(INICIO_DEMO), id);
            assertFalse(c.getFechaHoraFin().toLocalTime().isAfter(FIN_DEMO), id);
            assertEquals(Rol.FISIOTERAPEUTA, c.getFisioterapeuta().getRol(), id);
            assertTrue(c.getFisioterapeuta().isActivo(), id);
            assertTrue(c.getTratamiento().isActivo(), id);
            switch (c.getEstado()) {
                case PROGRAMADA -> assertTrue(c.getFechaHoraFin().isAfter(ahora),
                        id + ": una cita programada no puede haber terminado ya");
                case REALIZADA -> assertFalse(c.getFechaHoraFin().isAfter(ahora),
                        id + ": una cita realizada debe haber terminado");
                case NO_ASISTIDA -> assertTrue(c.getFechaHora().isBefore(ahora), id);
                default -> { }
            }
            primeraCitaPorPaciente.merge(c.getPaciente().getId(), c.getFechaHora().toLocalDate(),
                    (a, b) -> a.isBefore(b) ? a : b);
        }

        // Ningún solape entre citas que ocupan agenda (mismo fisio o mismo paciente)
        for (int i = 0; i < citas.size(); i++) {
            for (int j = i + 1; j < citas.size(); j++) {
                Cita a = citas.get(i);
                Cita b = citas.get(j);
                boolean ocupan = ocupa(a) && ocupa(b);
                boolean mismoRecurso = a.getFisioterapeuta().getId() == b.getFisioterapeuta().getId()
                        || a.getPaciente().getId() == b.getPaciente().getId();
                assertFalse(ocupan && mismoRecurso && a.seSolapaCon(b),
                        "Solape entre " + a.getFechaHora() + " y " + b.getFechaHora());
            }
        }

        // Las citas programadas superan la validación real de CitaService (se re-guardan como edición; las demás
        // ya no se pueden editar) y las realizadas o no asistidas ya podían marcarse a esta hora.
        Sesion.getInstance().iniciar(usuarioDAO.buscarPorUsername("recepcion").orElseThrow());
        CitaService servicio = new CitaService(citaDAO, pacienteDAO, usuarioDAO, tratamientoDAO, reloj);
        for (Cita c : citas) {
            if (c.getEstado() == EstadoCita.PROGRAMADA) {
                assertDoesNotThrow(() -> servicio.guardar(c), "Cita del " + c.getFechaHora());
            } else if (c.getEstado() != EstadoCita.CANCELADA) {
                assertTrue(servicio.puedeMarcarseAsistencia(c), "Cita del " + c.getFechaHora());
            }
        }

        // Citas "de hoy" para ambos fisioterapeutas, próximas citas y pasado con los tres estados
        for (String username : List.of("fisio", "fisio2")) {
            int fisioId = usuarioDAO.buscarPorUsername(username).orElseThrow().getId();
            assertTrue(citaDAO.listarPorDia(referencia, fisioId).size() >= 3, username);
        }
        long proximas = citas.stream().filter(c -> c.getFechaHora().toLocalDate().isAfter(referencia)
                && !c.getFechaHora().toLocalDate().isAfter(hoy.plusDays(7))
                && c.getEstado() == EstadoCita.PROGRAMADA).count();
        assertTrue(proximas >= 5, "Citas en los próximos 7 días: " + proximas);
        for (EstadoCita estado : List.of(EstadoCita.REALIZADA, EstadoCita.NO_ASISTIDA, EstadoCita.CANCELADA)) {
            assertTrue(citas.stream().anyMatch(c -> c.getFechaHora().toLocalDate().isBefore(hoy)
                    && c.getEstado() == estado), "Falta una cita pasada en estado " + estado);
        }

        // Pacientes: DNI/NIE, teléfono y email válidos y alta anterior a su primera cita
        List<Paciente> pacientes = pacienteDAO.listarTodos();
        assertEquals(12, pacientes.size());
        for (Paciente p : pacientes) {
            assertTrue(Validador.esDniNieValido(p.getDni()), p.getDni());
            assertTrue(Validador.esTelefonoValido(p.getTelefono()) && p.getTelefono().startsWith("6"), p.getTelefono());
            assertTrue(p.getEmail() == null || (Validador.esEmailValido(p.getEmail())
                    && p.getEmail().endsWith("@example.com")), p.getEmail());
            assertFalse(p.getFechaAlta().isAfter(hoy));
            LocalDate primera = primeraCitaPorPaciente.get(p.getId());
            assertTrue(primera == null || !p.getFechaAlta().isAfter(primera), p.getNombreCompleto());
        }

        // Notas clínicas: ~10, ligadas a citas realizadas del mismo paciente y escritas por su fisio
        int totalNotas = 0;
        for (Paciente p : pacientes) {
            for (NotaClinica n : notaDAO.listarPorPaciente(p.getId())) {
                totalNotas++;
                Cita cita = citaDAO.buscarPorId(n.getCitaId()).orElseThrow();
                assertEquals(EstadoCita.REALIZADA, cita.getEstado());
                assertEquals(p.getId(), cita.getPaciente().getId());
                assertEquals(cita.getFisioterapeuta().getId(), n.getAutor().getId());
                assertFalse(n.getFecha().isAfter(ahora));
                assertTrue(n.getEscalaDolor() >= 0 && n.getEscalaDolor() <= 10);
                assertTrue(n.getContenido().length() >= 5 && n.getContenido().length() <= 2000);
            }
        }
        assertTrue(totalNotas >= 8 && totalNotas <= 14, "~10 notas: " + totalNotas);
    }

    @Test
    void lasNotasDeDemostracionUsanElSimboloDeGrado() {
        EsquemaBD.inicializar();
        for (Paciente p : pacienteDAO.listarTodos()) {
            for (NotaClinica n : notaDAO.listarPorPaciente(p.getId())) {
                assertFalse(n.getContenido().contains("º"), "Ordinal en lugar de grado: " + n.getContenido());
            }
        }
        assertTrue(contarNotasCon("°") > 0, "Las notas de ejemplo con ángulos usan °");
    }

    @Test
    void lasConsultasPorFechaUsanUnIndice() throws SQLException {
        EsquemaBD.crearTablas();
        // Consulta de la agenda de todos los fisioterapeutas y de las estadísticas por rango de fechas
        String plan = planDe("SELECT id FROM cita WHERE fecha_hora >= '2030-01-07T00:00' AND fecha_hora < '2030-01-08T00:00'")
                + planDe("SELECT COUNT(*) FROM cita WHERE estado = 'REALIZADA' AND fecha_hora >= '2030-01-01T00:00' "
                + "AND fecha_hora < '2030-02-01T00:00'");
        assertTrue(plan.contains("idx_cita_fecha"), plan);
        assertFalse(plan.contains("SCAN cita"), "Sin recorrer la tabla entera: " + plan);
    }

    private static String planDe(String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (Statement st = Database.getInstance().getConexion().createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString("detail")).append('\n');
            }
        }
        return plan.toString();
    }

    private int contarNotasCon(String texto) {
        try (Statement st = Database.getInstance().getConexion().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM nota_clinica WHERE instr(contenido, '" + texto + "') > 0")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void cargaUsuariosYTratamientosDeLaEspecificacion() {
        EsquemaBD.inicializar();
        List<Usuario> usuarios = usuarioDAO.listarTodos();
        assertEquals(4, usuarios.size());
        assertEquals(Rol.ADMIN, usuarioDAO.buscarPorUsername("admin").orElseThrow().getRol());
        assertEquals("Laura Martín Ruiz", usuarioDAO.buscarPorUsername("admin").orElseThrow().getNombreCompleto());
        assertEquals(2, usuarioDAO.listarPorRol(Rol.FISIOTERAPEUTA).size());
        assertEquals(Rol.RECEPCION, usuarioDAO.buscarPorUsername("recepcion").orElseThrow().getRol());
        assertEquals(7, tratamientoDAO.listarActivos().size());
        assertTrue(tratamientoDAO.listarTodos().stream().anyMatch(t -> t.getNombre().equals("Rehabilitación postquirúrgica")
                && t.getDuracionMinutos() == 60 && t.getPrecio() == 45.0));
    }

    @Test
    void losUsuariosDemoEntranConLasContrasenasDocumentadas() {
        EsquemaBD.inicializar();
        AuthService auth = new AuthService(usuarioDAO, Clock.systemDefaultZone());
        Map<String, String> credenciales = Map.of("admin", "Admin1234!", "fisio", "Fisio1234!",
                "fisio2", "Fisio1234!", "recepcion", "Recep1234!");
        credenciales.forEach((usuario, password) -> {
            assertEquals(usuario, auth.login(usuario, password.toCharArray()).getUsername());
            auth.logout();
        });
    }

    @Test
    void inicializarDosVecesNoDuplicaDatos() {
        EsquemaBD.inicializar();
        int citas = contar("cita");
        EsquemaBD.inicializar();
        assertEquals(4, contar("usuario"));
        assertEquals(12, contar("paciente"));
        assertEquals(citas, contar("cita"));
    }

    @Test
    void laConexionTieneLasClavesForaneasActivadas() throws SQLException {
        EsquemaBD.crearTablas();
        try (Statement st = Database.getInstance().getConexion().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        assertEquals(0, contar("usuario"), "crearTablas no carga datos");
    }

    @Test
    void diaDeReferenciaEvitaElDomingo() {
        assertEquals(LocalDate.of(2026, 10, 12), EsquemaBD.diaDeReferencia(LocalDate.of(2026, 10, 11)));
        assertEquals(LocalDate.of(2026, 10, 17), EsquemaBD.diaDeReferencia(LocalDate.of(2026, 10, 17)));
    }

    private int contar(String tabla) {
        try (Statement st = Database.getInstance().getConexion().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean ocupa(Cita c) {
        return c.getEstado() == EstadoCita.PROGRAMADA || c.getEstado() == EstadoCita.REALIZADA;
    }
}
