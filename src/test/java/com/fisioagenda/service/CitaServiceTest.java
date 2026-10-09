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
import com.fisioagenda.security.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.fisioagenda.BdPruebas.LUNES;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitaServiceTest {

    /** "Ahora" en las pruebas: lunes 7 de enero de 2030 a las 07:00, antes de abrir. */
    private static final LocalDateTime AHORA = LUNES.atTime(7, 0);

    @TempDir
    Path carpeta;

    private SqliteUsuarioDAO usuarioDAO;
    private SqliteCitaDAO citaDAO;
    private SqliteTratamientoDAO tratamientoDAO;
    private CitaService servicio;

    private Usuario admin;
    private Usuario recepcion;
    private Usuario fisio;
    private Usuario fisio2;
    private Paciente lucia;
    private Paciente javier;
    private Tratamiento general;
    private Tratamiento masaje;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        usuarioDAO = new SqliteUsuarioDAO(database);
        SqlitePacienteDAO pacienteDAO = new SqlitePacienteDAO(database);
        tratamientoDAO = new SqliteTratamientoDAO(database);
        citaDAO = new SqliteCitaDAO(database);
        servicio = new CitaService(citaDAO, pacienteDAO, usuarioDAO, tratamientoDAO, BdPruebas.reloj(AHORA));

        admin = BdPruebas.usuario(usuarioDAO, "admin", Rol.ADMIN);
        recepcion = BdPruebas.usuario(usuarioDAO, "recepcion", Rol.RECEPCION);
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        fisio2 = BdPruebas.usuario(usuarioDAO, "fisio2", Rol.FISIOTERAPEUTA);
        lucia = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        javier = BdPruebas.paciente(pacienteDAO, "11111111H", "Javier", "Romero Navarro");
        general = BdPruebas.tratamiento(tratamientoDAO, "Fisioterapia general", 45, 35);
        masaje = BdPruebas.tratamiento(tratamientoDAO, "Masaje descontracturante", 30, 30);
        Sesion.getInstance().iniciar(recepcion);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void crearCitaValidaQuedaProgramadaYCargaSusDatos() {
        Cita c = BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0));
        c.setEstado(EstadoCita.REALIZADA);
        c.setNotas("  Trae informe  ");
        Cita guardada = servicio.guardar(c);

        assertTrue(guardada.getId() > 0);
        assertEquals(EstadoCita.PROGRAMADA, guardada.getEstado(), "Toda cita nueva nace programada");
        assertEquals("Trae informe", guardada.getNotas());
        assertEquals(LUNES.atTime(10, 45), guardada.getFechaHoraFin());
        assertEquals(1, servicio.citasDelDia(LUNES, fisio.getId()).size());
        assertEquals(0, servicio.citasDelDia(LUNES, fisio2.getId()).size());
        assertEquals(1, servicio.citasDePaciente(lucia.getId()).size());
    }

    @Test
    void solapeConElMismoFisioterapeuta() {
        servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(javier, fisio, masaje, LUNES.atTime(10, 30))));
        assertTrue(e.getMessage().contains("10:00 a 10:45"), "El mensaje indica la hora ocupada: " + e.getMessage());
        assertTrue(e.getMessage().contains("fisioterapeuta"));
    }

    @Test
    void solapeConElMismoPaciente() {
        servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio2, masaje, LUNES.atTime(9, 45))));
        assertTrue(e.getMessage().contains("paciente"), e.getMessage());
        assertTrue(e.getMessage().contains("10:00 a 10:45"), e.getMessage());
    }

    @Test
    void citasContiguasNoSeSolapan() {
        servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(javier, fisio, masaje, LUNES.atTime(10, 45))));
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(javier, fisio, masaje, LUNES.atTime(9, 30))));
    }

    @Test
    void unaCitaCanceladaNoOcupaLaFranja() {
        Cita primera = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        servicio.cambiarEstado(primera.getId(), EstadoCita.CANCELADA);
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(javier, fisio, general, LUNES.atTime(10, 0))));
    }

    @ParameterizedTest(name = "{0} con {1} min fuera de horario")
    @CsvSource({"07:45, 45", "07:59, 30", "20:30, 45", "20:45, 30", "21:00, 30", "23:30, 45"})
    void fueraDeHorario(String hora, int duracion) {
        Tratamiento t = duracion == 45 ? general : masaje;
        Cita c = BdPruebas.cita(lucia, fisio, t, LUNES.atTime(LocalTime.parse(hora)));
        ServiceException e = assertThrows(ServiceException.class, () -> servicio.guardar(c));
        assertTrue(e.getMessage().contains("horario"), e.getMessage());
    }

    @Test
    void cadaIncumplimientoDelHorarioTieneSuPropioMensaje() {
        // Empezar antes de abrir: el mensaje habla de la apertura, no de una hora de fin que sí cabría
        ServiceException temprano = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, masaje, LUNES.atTime(7, 30))));
        assertTrue(temprano.getMessage().startsWith("La clínica abre a las 08:00"), temprano.getMessage());
        assertFalse(temprano.getMessage().contains("terminaría"), temprano.getMessage());

        ServiceException alCierre = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, masaje, LUNES.atTime(21, 0))));
        assertTrue(alCierre.getMessage().startsWith("La clínica cierra a las 21:00"), alCierre.getMessage());

        ServiceException largo = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(20, 30))));
        assertTrue(largo.getMessage().contains("terminaría a las 21:15"), largo.getMessage());

        ServiceException trasMedianoche = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(23, 30))));
        assertTrue(trasMedianoche.getMessage().startsWith("La clínica cierra"), trasMedianoche.getMessage());
    }

    @Test
    void limitesDelHorarioIncluidos() {
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(8, 0))));
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(20, 15))),
                "Una cita que termina justo a las 21:00 es válida");
    }

    @Test
    void domingoNoSeAbre() {
        LocalDate domingo = LocalDate.of(2030, 1, 13);
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, domingo.atTime(10, 0))));
        assertTrue(e.getMessage().contains("domingo"), e.getMessage());
    }

    @Test
    void sabadoSiSeAbre() {
        LocalDate sabado = LocalDate.of(2030, 1, 12);
        assertDoesNotThrow(() -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, sabado.atTime(10, 0))));
    }

    @Test
    void noSePuedeCrearUnaCitaEnElPasado() {
        LocalDate sabadoAnterior = LUNES.minusDays(2);
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, sabadoAnterior.atTime(10, 0))));
        assertTrue(e.getMessage().contains("pasada"), e.getMessage());
    }

    @Test
    void editarSinAutoSolapeYPermitiendoFechaPasada() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        // Mover 15 minutos la misma cita se solaparía consigo misma si no se excluyera
        c.setFechaHora(LUNES.atTime(10, 15));
        c.setNotas("Retrasada un cuarto de hora");
        Cita editada = assertDoesNotThrow(() -> servicio.guardar(c));
        assertEquals(LUNES.atTime(10, 15), editada.getFechaHora());
        assertEquals(1, servicio.citasDelDia(LUNES, null).size());

        // Una cita ya existente se puede editar aunque su fecha haya pasado
        CitaService masTarde = new CitaService(citaDAO, new SqlitePacienteDAO(Database.getInstance()), usuarioDAO,
                tratamientoDAO, BdPruebas.reloj(LUNES.plusDays(3).atTime(9, 0)));
        editada.setNotas("Corrección posterior");
        assertDoesNotThrow(() -> masTarde.guardar(editada));
    }

    @Test
    void editarNoPuedeProvocarSolapeConOtraCita() {
        servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        Cita otra = servicio.guardar(BdPruebas.cita(javier, fisio, general, LUNES.atTime(12, 0)));
        otra.setFechaHora(LUNES.atTime(10, 30));
        assertThrows(ServiceException.class, () -> servicio.guardar(otra));
    }

    @Test
    void elProfesionalDebeSerFisioterapeutaActivoYElTratamientoActivo() {
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, admin, general, LUNES.atTime(10, 0))));

        Usuario inactivo = usuarioDAO.buscarPorId(fisio2.getId()).orElseThrow();
        inactivo.setActivo(false);
        usuarioDAO.actualizar(inactivo);
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio2, general, LUNES.atTime(10, 0))));

        masaje.setActivo(false);
        tratamientoDAO.actualizar(masaje);
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, masaje, LUNES.atTime(10, 0))));
        assertTrue(e.getMessage().contains("no está activo"), e.getMessage());
    }

    @Test
    void faltanDatosObligatorios() {
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(null, fisio, general, LUNES.atTime(10, 0))));
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, null, general, LUNES.atTime(10, 0))));
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, null, LUNES.atTime(10, 0))));
        assertThrows(ServiceException.class, () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, null)));
    }

    @Test
    void huecosLibresCada15MinutosSinSolapes() {
        servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));     // 10:00-10:45
        Cita cancelada = servicio.guardar(BdPruebas.cita(javier, fisio, general, LUNES.atTime(12, 0)));
        servicio.cambiarEstado(cancelada.getId(), EstadoCita.CANCELADA);
        servicio.guardar(BdPruebas.cita(javier, fisio2, general, LUNES.atTime(16, 0)));   // otro fisio

        List<LocalTime> huecos = servicio.huecosLibres(LUNES, fisio.getId(), 30);
        assertEquals(LocalTime.of(8, 0), huecos.get(0));
        assertEquals(LocalTime.of(20, 30), huecos.get(huecos.size() - 1), "La última termina a las 21:00");
        assertTrue(huecos.contains(LocalTime.of(9, 30)));
        assertFalse(huecos.contains(LocalTime.of(9, 45)));
        assertFalse(huecos.contains(LocalTime.of(10, 30)));
        assertTrue(huecos.contains(LocalTime.of(10, 45)));
        assertTrue(huecos.contains(LocalTime.of(12, 0)), "La cita cancelada no ocupa hueco");
        assertTrue(huecos.contains(LocalTime.of(16, 0)), "Las citas de otro fisio no cuentan");
        assertEquals(51 - 4, huecos.size());
    }

    @Test
    void huecosLibresHoySoloFuturosYNingunoEnDomingoOPasado() {
        CitaService aMediodia = new CitaService(citaDAO, new SqlitePacienteDAO(Database.getInstance()), usuarioDAO,
                tratamientoDAO, BdPruebas.reloj(LUNES.atTime(12, 10)));
        assertEquals(LocalTime.of(12, 15), aMediodia.huecosLibres(LUNES, fisio.getId(), 45).get(0));
        assertTrue(servicio.huecosLibres(LocalDate.of(2030, 1, 13), fisio.getId(), 45).isEmpty());
        assertTrue(servicio.huecosLibres(LUNES.minusDays(2), fisio.getId(), 45).isEmpty());
    }

    @Test
    void huecosLibresExcluyendoLaCitaQueSeEdita() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        assertFalse(servicio.huecosLibres(LUNES, fisio.getId(), 45).contains(LocalTime.of(10, 0)));
        assertTrue(servicio.huecosLibres(LUNES, fisio.getId(), 45, c.getId()).contains(LocalTime.of(10, 0)));
    }

    @Test
    void recepcionNoPuedeMarcarRealizadaPeroSiCancelar() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        ServiceException e = assertThrows(ServiceException.class,
                () -> servicio.cambiarEstado(c.getId(), EstadoCita.REALIZADA));
        assertTrue(e.getMessage().startsWith("No tiene permisos para"), e.getMessage());
        assertThrows(ServiceException.class, () -> servicio.cambiarEstado(c.getId(), EstadoCita.NO_ASISTIDA));

        servicio.cambiarEstado(c.getId(), EstadoCita.CANCELADA);
        assertEquals(EstadoCita.CANCELADA, citaDAO.buscarPorId(c.getId()).orElseThrow().getEstado());
    }

    @Test
    void fisioterapeutaMarcaRealizadaYCorrigeANoAsistida() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        CitaService alTerminar = servicioA(LUNES.atTime(10, 45));
        Sesion.getInstance().iniciar(fisio);
        alTerminar.cambiarEstado(c.getId(), EstadoCita.REALIZADA);
        assertEquals(EstadoCita.REALIZADA, citaDAO.buscarPorId(c.getId()).orElseThrow().getEstado());
        alTerminar.cambiarEstado(c.getId(), EstadoCita.NO_ASISTIDA);
        assertEquals(EstadoCita.NO_ASISTIDA, citaDAO.buscarPorId(c.getId()).orElseThrow().getEstado());

        assertThrows(ServiceException.class, () -> alTerminar.cambiarEstado(c.getId(), EstadoCita.CANCELADA),
                "Solo se cancelan citas programadas");
        assertThrows(ServiceException.class, () -> alTerminar.cambiarEstado(c.getId(), EstadoCita.PROGRAMADA));
    }

    @Test
    void laAsistenciaSoloSeMarcaDesdeLaHoraDeInicio() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        Sesion.getInstance().iniciar(fisio);

        // A las 07:00 la cita de las 10:00 aún no ha empezado: no se puede dar por "no asistida" (liberaría su hueco)
        assertFalse(servicio.puedeMarcarseAsistencia(c));
        for (EstadoCita estado : List.of(EstadoCita.NO_ASISTIDA, EstadoCita.REALIZADA)) {
            ServiceException e = assertThrows(ServiceException.class, () -> servicio.cambiarEstado(c.getId(), estado));
            assertTrue(e.getMessage().contains("todavía no ha empezado") && e.getMessage().contains("10:00"),
                    e.getMessage());
        }
        assertEquals(EstadoCita.PROGRAMADA, citaDAO.buscarPorId(c.getId()).orElseThrow().getEstado());
        assertFalse(servicioA(LUNES.atTime(9, 59)).puedeMarcarseAsistencia(c));

        // Desde la hora de inicio, sí
        CitaService aLaHora = servicioA(LUNES.atTime(10, 0));
        assertTrue(aLaHora.puedeMarcarseAsistencia(c));
        aLaHora.cambiarEstado(c.getId(), EstadoCita.NO_ASISTIDA);
        assertEquals(EstadoCita.NO_ASISTIDA, citaDAO.buscarPorId(c.getId()).orElseThrow().getEstado());
    }

    @Test
    void soloSeEditanCitasProgramadas() {
        Cita realizada = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        Cita cancelada = servicio.guardar(BdPruebas.cita(javier, fisio, masaje, LUNES.atTime(12, 0)));
        Sesion.getInstance().iniciar(fisio);
        servicioA(LUNES.atTime(11, 0)).cambiarEstado(realizada.getId(), EstadoCita.REALIZADA);
        servicio.cambiarEstado(cancelada.getId(), EstadoCita.CANCELADA);

        // Editarlas las devolvería a PROGRAMADA (resucitando una cancelada o "desmarcando" una realizada)
        for (Cita c : List.of(realizada, cancelada)) {
            Cita cambios = citaDAO.buscarPorId(c.getId()).orElseThrow();
            EstadoCita estado = cambios.getEstado();
            cambios.setNotas("Cambio posterior");
            ServiceException e = assertThrows(ServiceException.class, () -> servicio.guardar(cambios));
            assertTrue(e.getMessage().startsWith("Solo se pueden editar citas programadas"), e.getMessage());
            Cita leida = citaDAO.buscarPorId(c.getId()).orElseThrow();
            assertEquals(estado, leida.getEstado());
            assertEquals(null, leida.getNotas());
        }
    }

    @Test
    void unaCitaSigueEditableSiSuFisioterapeutaCambiaDeRol() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio2, general, LUNES.atTime(10, 0)));
        Usuario ahoraRecepcion = usuarioDAO.buscarPorId(fisio2.getId()).orElseThrow();
        ahoraRecepcion.setRol(Rol.RECEPCION);
        usuarioDAO.actualizar(ahoraRecepcion);

        // Sin cambiar de profesional se puede editar (por ejemplo, para moverla de hora)...
        c.setFechaHora(LUNES.atTime(11, 0));
        assertDoesNotThrow(() -> servicio.guardar(c));
        // ...y reasignarla a un fisioterapeuta activo
        c.setFisioterapeuta(fisio);
        assertDoesNotThrow(() -> servicio.guardar(c));
        // pero no se le puede asignar a quien ya no es fisioterapeuta
        c.setFisioterapeuta(ahoraRecepcion);
        assertThrows(ServiceException.class, () -> servicio.guardar(c));
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(javier, ahoraRecepcion, general, LUNES.atTime(12, 0))));
    }

    @Test
    void noSeMarcaRealizadaUnaCitaDeUnDiaFuturo() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.plusDays(1).atTime(10, 0)));
        Sesion.getInstance().iniciar(admin);
        assertThrows(ServiceException.class, () -> servicio.cambiarEstado(c.getId(), EstadoCita.REALIZADA));
    }

    @Test
    void unaCitaCanceladaNoCambiaDeEstado() {
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        servicio.cambiarEstado(c.getId(), EstadoCita.CANCELADA);
        Sesion.getInstance().iniciar(fisio);
        assertThrows(ServiceException.class, () -> servicio.cambiarEstado(c.getId(), EstadoCita.REALIZADA));
    }

    @Test
    void reactivarUnaNoAsistidaComoRealizadaCompruebaSolapes() {
        Sesion.getInstance().iniciar(fisio);
        CitaService aLasDiez = servicioA(LUNES.atTime(10, 0));
        Cita c = servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0)));
        aLasDiez.cambiarEstado(c.getId(), EstadoCita.NO_ASISTIDA);
        // Se aprovecha el hueco con otro paciente...
        Cita otra = aLasDiez.guardar(BdPruebas.cita(javier, fisio, general, LUNES.atTime(10, 0)));
        aLasDiez.cambiarEstado(otra.getId(), EstadoCita.REALIZADA);
        // ...y ya no se puede marcar la primera como realizada
        assertThrows(ServiceException.class, () -> aLasDiez.cambiarEstado(c.getId(), EstadoCita.REALIZADA));
    }

    /** Servicio que comparte la base de datos de la prueba pero con otro "ahora". */
    private CitaService servicioA(LocalDateTime ahora) {
        return new CitaService(citaDAO, new SqlitePacienteDAO(Database.getInstance()), usuarioDAO, tratamientoDAO,
                BdPruebas.reloj(ahora));
    }

    @Test
    void sinSesionNoSePuedeOperar() {
        Sesion.getInstance().cerrar();
        assertThrows(ServiceException.class, () -> servicio.citasDelDia(LUNES, null));
        assertThrows(ServiceException.class,
                () -> servicio.guardar(BdPruebas.cita(lucia, fisio, general, LUNES.atTime(10, 0))));
    }
}
