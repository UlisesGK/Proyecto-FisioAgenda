package com.fisioagenda.dao;

import com.fisioagenda.BdPruebas;
import com.fisioagenda.dao.sqlite.SqliteCitaDAO;
import com.fisioagenda.dao.sqlite.SqliteNotaClinicaDAO;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteTratamientoDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de las implementaciones SQLite de los DAO sobre una base de datos en un fichero temporal. */
class SqliteDaoTest {

    @TempDir
    Path carpeta;

    private SqliteUsuarioDAO usuarioDAO;
    private SqlitePacienteDAO pacienteDAO;
    private SqliteTratamientoDAO tratamientoDAO;
    private SqliteCitaDAO citaDAO;
    private SqliteNotaClinicaDAO notaDAO;

    private Usuario fisio;
    private Tratamiento masaje;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        usuarioDAO = new SqliteUsuarioDAO(database);
        pacienteDAO = new SqlitePacienteDAO(database);
        tratamientoDAO = new SqliteTratamientoDAO(database);
        citaDAO = new SqliteCitaDAO(database);
        notaDAO = new SqliteNotaClinicaDAO(database);

        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        masaje = BdPruebas.tratamiento(tratamientoDAO, "Masaje", 30, 30);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void laRutaSePuedeIndicarConLaPropiedadDeSistema() {
        Path fichero = carpeta.resolve("datos").resolve("propiedad.db");
        System.setProperty(Database.PROPIEDAD_RUTA, fichero.toString());
        try {
            Database.configurar(null);
            Database database = Database.getInstance();
            assertEquals(fichero.toString(), database.getRuta());
            database.getConexion();
            assertTrue(Files.exists(fichero), "Crea la carpeta y el fichero de la base de datos");
        } finally {
            System.clearProperty(Database.PROPIEDAD_RUTA);
            Database.configurar(null);
        }
    }

    @Test
    void crudDePaciente() {
        Paciente p = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        assertTrue(p.getId() > 0);

        Paciente leido = pacienteDAO.buscarPorId(p.getId()).orElseThrow();
        assertEquals("Lucía", leido.getNombre());
        assertEquals("Fernández Gil", leido.getApellidos());
        assertEquals(LocalDate.of(1990, 5, 20), leido.getFechaNacimiento());
        assertEquals(LocalDate.of(2025, 1, 15), leido.getFechaAlta());

        leido.setTelefono("699888777");
        leido.setEmail(null);
        leido.setObservaciones("Alergia al látex");
        pacienteDAO.actualizar(leido);
        Paciente actualizado = pacienteDAO.buscarPorDni("12345678Z").orElseThrow();
        assertEquals("699888777", actualizado.getTelefono());
        assertNull(actualizado.getEmail());
        assertEquals("Alergia al látex", actualizado.getObservaciones());

        assertEquals(1, pacienteDAO.contar());
        pacienteDAO.eliminar(p.getId());
        assertTrue(pacienteDAO.buscarPorId(p.getId()).isEmpty());
        assertEquals(0, pacienteDAO.contar());
    }

    @Test
    void buscarPacientesPorNombreApellidosDniYTelefonoSinDistinguirMayusculas() {
        BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        BdPruebas.paciente(pacienteDAO, "11111111H", "Javier", "Romero Navarro");
        BdPruebas.paciente(pacienteDAO, "22222222J", "Elena", "Castro Molina");

        assertEquals(List.of("Romero Navarro"), apellidos(pacienteDAO.buscar("ROMERO")));
        assertEquals(List.of("Castro Molina"), apellidos(pacienteDAO.buscar("elena castro")));
        assertEquals(List.of("Fernández Gil"), apellidos(pacienteDAO.buscar("5678z")));
        assertEquals(3, pacienteDAO.buscar("612345").size());
        assertEquals(3, pacienteDAO.buscar("  ").size());
        assertTrue(pacienteDAO.buscar("%").isEmpty(), "Los comodines de LIKE se buscan de forma literal");
        assertTrue(pacienteDAO.buscar("_").isEmpty());
        // Sin distinguir tildes ni mayúsculas acentuadas (LIKE de SQLite solo lo hace con letras ASCII)
        assertEquals(List.of("Fernández Gil"), apellidos(pacienteDAO.buscar("lucia fernandez")));
        assertEquals(List.of("Fernández Gil"), apellidos(pacienteDAO.buscar("FERNÁNDEZ")));
        BdPruebas.paciente(pacienteDAO, "33333333P", "Álvaro", "Núñez Ortiz");
        assertEquals(List.of("Núñez Ortiz"), apellidos(pacienteDAO.buscar("alvaro")));
        assertEquals(List.of("Núñez Ortiz"), apellidos(pacienteDAO.buscar("ÁLVARO")));
        assertEquals(List.of("Núñez Ortiz"), apellidos(pacienteDAO.buscar("NÚÑEZ")));
        assertEquals(List.of("Núñez Ortiz"), apellidos(pacienteDAO.buscar("ortiz nunez")));
        assertTrue(pacienteDAO.buscar("álvaro castro").isEmpty(), "Deben aparecer todas las palabras");
        // Ordenados por apellidos
        assertEquals(List.of("Castro Molina", "Fernández Gil", "Núñez Ortiz", "Romero Navarro"),
                apellidos(pacienteDAO.listarTodos()));
    }

    @Test
    void elDniDebeSerUnico() {
        BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        assertThrows(DataAccessException.class,
                () -> BdPruebas.paciente(pacienteDAO, "12345678Z", "Otra", "Persona"));
    }

    @Test
    void lasCitasSeCarganConSusRelacionesMedianteJoin() {
        Paciente p = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        Usuario fisio2 = BdPruebas.usuario(usuarioDAO, "fisio2", Rol.FISIOTERAPEUTA);
        LocalDate dia = LocalDate.of(2030, 1, 7);
        Cita c1 = citaDAO.insertar(BdPruebas.cita(p, fisio, masaje, dia.atTime(10, 0)));
        Cita c2 = BdPruebas.cita(p, fisio2, masaje, dia.atTime(9, 0));
        c2.setNotas("Primera visita");
        citaDAO.insertar(c2);
        citaDAO.insertar(BdPruebas.cita(p, fisio, masaje, dia.plusDays(1).atTime(9, 0)));

        Cita leida = citaDAO.buscarPorId(c1.getId()).orElseThrow();
        assertEquals("Lucía Fernández Gil", leida.getPaciente().getNombreCompleto());
        assertEquals("Nombre de fisio", leida.getFisioterapeuta().getNombreCompleto());
        assertEquals(Rol.FISIOTERAPEUTA, leida.getFisioterapeuta().getRol());
        assertNull(leida.getFisioterapeuta().getPasswordHash(), "El JOIN no debe cargar credenciales");
        assertEquals(30, leida.getTratamiento().getDuracionMinutos());
        assertEquals(dia.atTime(10, 30), leida.getFechaHoraFin());
        assertEquals(EstadoCita.PROGRAMADA, leida.getEstado());

        List<Cita> delDia = citaDAO.listarPorDia(dia, null);
        assertEquals(2, delDia.size());
        assertEquals(dia.atTime(9, 0), delDia.get(0).getFechaHora(), "Ordenadas por hora");
        assertEquals("Primera visita", delDia.get(0).getNotas());
        assertEquals(1, citaDAO.listarPorDia(dia, fisio.getId()).size());
        assertEquals(2, citaDAO.listarEntre(dia.atTime(9, 30), dia.plusDays(2).atStartOfDay(), null).size());
        assertEquals(3, citaDAO.listarPorPaciente(p.getId()).size());
        assertEquals(dia.plusDays(1).atTime(9, 0), citaDAO.listarPorPaciente(p.getId()).get(0).getFechaHora(),
                "Las citas del paciente van de la más reciente a la más antigua");
    }

    @Test
    void actualizarCitaYContarPorEstado() {
        Paciente p = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 7, 10, 0);
        Cita cita = citaDAO.insertar(BdPruebas.cita(p, fisio, masaje, inicio));

        cita.setFechaHora(inicio.plusHours(2));
        citaDAO.actualizar(cita);
        citaDAO.actualizarEstado(cita.getId(), EstadoCita.REALIZADA);

        Cita leida = citaDAO.buscarPorId(cita.getId()).orElseThrow();
        assertEquals(inicio.plusHours(2), leida.getFechaHora());
        assertEquals(EstadoCita.REALIZADA, leida.getEstado());
        assertEquals(1, citaDAO.contarPorEstadoEntre(EstadoCita.REALIZADA, inicio, inicio.plusDays(1)));
        assertEquals(0, citaDAO.contarPorEstadoEntre(EstadoCita.REALIZADA, inicio.plusHours(3), inicio.plusDays(1)));

        citaDAO.eliminar(cita.getId());
        assertTrue(citaDAO.buscarPorId(cita.getId()).isEmpty());
    }

    @Test
    void lasClavesForaneasEstanActivadas() {
        Paciente inexistente = new Paciente();
        inexistente.setId(999);
        assertThrows(DataAccessException.class,
                () -> citaDAO.insertar(BdPruebas.cita(inexistente, fisio, masaje, LocalDateTime.of(2030, 1, 7, 10, 0))));
    }

    @Test
    void borrarPacienteBorraEnCascadaSusCitasYNotas() {
        Paciente p = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        Paciente otro = BdPruebas.paciente(pacienteDAO, "11111111H", "Javier", "Romero Navarro");
        Cita cita = citaDAO.insertar(BdPruebas.cita(p, fisio, masaje, LocalDateTime.of(2030, 1, 7, 10, 0)));
        citaDAO.insertar(BdPruebas.cita(otro, fisio, masaje, LocalDateTime.of(2030, 1, 7, 11, 0)));
        notaDAO.insertar(nota(p.getId(), cita.getId(), 5));

        pacienteDAO.eliminar(p.getId());

        assertTrue(citaDAO.listarPorPaciente(p.getId()).isEmpty());
        assertTrue(citaDAO.buscarPorId(cita.getId()).isEmpty());
        assertTrue(notaDAO.listarPorPaciente(p.getId()).isEmpty());
        assertEquals(1, citaDAO.listarPorPaciente(otro.getId()).size(), "Las citas de otros pacientes no se tocan");
    }

    @Test
    void notasClinicasConAutorYOrdenDescendente() {
        Paciente p = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        Cita cita = citaDAO.insertar(BdPruebas.cita(p, fisio, masaje, LocalDateTime.of(2030, 1, 7, 10, 0)));
        NotaClinica primera = nota(p.getId(), cita.getId(), 7);
        primera.setFecha(LocalDateTime.of(2030, 1, 7, 10, 30));
        notaDAO.insertar(primera);
        NotaClinica segunda = nota(p.getId(), null, 4);
        segunda.setFecha(LocalDateTime.of(2030, 1, 14, 10, 30));
        notaDAO.insertar(segunda);

        List<NotaClinica> notas = notaDAO.listarPorPaciente(p.getId());
        assertEquals(2, notas.size());
        assertEquals(4, notas.get(0).getEscalaDolor(), "La más reciente primero");
        assertNull(notas.get(0).getCitaId());
        assertEquals(cita.getId(), notas.get(1).getCitaId());
        assertEquals("Nombre de fisio", notas.get(1).getAutor().getNombreCompleto());

        // Al borrar la cita, la nota se conserva sin referencia (ON DELETE SET NULL)
        citaDAO.eliminar(cita.getId());
        assertTrue(notaDAO.listarPorPaciente(p.getId()).stream().allMatch(n -> n.getCitaId() == null));
    }

    @Test
    void usuariosYControlDeAcceso() {
        Usuario leido = usuarioDAO.buscarPorUsername("fisio").orElseThrow();
        assertEquals(fisio.getId(), leido.getId());
        assertTrue(leido.isActivo());
        assertEquals(0, leido.getIntentosFallidos());
        assertNull(leido.getBloqueadoHasta());

        LocalDateTime bloqueo = LocalDateTime.of(2030, 1, 7, 10, 5, 42);
        leido.setIntentosFallidos(3);
        leido.setBloqueadoHasta(bloqueo);
        usuarioDAO.actualizarControlAcceso(leido);
        Usuario releido = usuarioDAO.buscarPorId(fisio.getId()).orElseThrow();
        assertEquals(3, releido.getIntentosFallidos());
        assertEquals(bloqueo, releido.getBloqueadoHasta(), "El bloqueo conserva los segundos");

        BdPruebas.usuario(usuarioDAO, "recepcion", Rol.RECEPCION);
        assertEquals(2, usuarioDAO.listarTodos().size());
        assertEquals(1, usuarioDAO.listarPorRol(Rol.FISIOTERAPEUTA).size());
        assertThrows(DataAccessException.class, () -> BdPruebas.usuario(usuarioDAO, "fisio", Rol.ADMIN));
    }

    @Test
    void tratamientosActivosEInactivos() {
        Tratamiento puncion = BdPruebas.tratamiento(tratamientoDAO, "Punción seca", 30, 40);
        puncion.setActivo(false);
        puncion.setDescripcion("Agujas de punción");
        tratamientoDAO.actualizar(puncion);

        assertEquals(2, tratamientoDAO.listarTodos().size());
        assertEquals(List.of("Masaje"), tratamientoDAO.listarActivos().stream().map(Tratamiento::getNombre).toList());
        Tratamiento leido = tratamientoDAO.buscarPorId(puncion.getId()).orElseThrow();
        assertEquals("Agujas de punción", leido.getDescripcion());
        assertEquals(40.0, leido.getPrecio());
    }

    private NotaClinica nota(int pacienteId, Integer citaId, int eva) {
        NotaClinica n = new NotaClinica();
        n.setPacienteId(pacienteId);
        n.setCitaId(citaId);
        n.setAutor(fisio);
        n.setFecha(LocalDateTime.of(2030, 1, 7, 11, 0));
        n.setContenido("Sesión de prueba");
        n.setEscalaDolor(eva);
        return n;
    }

    private static List<String> apellidos(List<Paciente> pacientes) {
        return pacientes.stream().map(Paciente::getApellidos).toList();
    }
}
