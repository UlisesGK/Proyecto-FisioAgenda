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

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadisticasServiceTest {

    /** Miércoles 9 de enero de 2030 a mediodía. */
    private static final LocalDate MIERCOLES = LocalDate.of(2030, 1, 9);

    @TempDir
    Path carpeta;

    private EstadisticasService servicio;
    private SqliteCitaDAO citaDAO;
    private Usuario admin;
    private Usuario fisio;
    private Usuario fisio2;
    private Paciente lucia;
    private Paciente javier;
    private Tratamiento general;

    @BeforeEach
    void setUp() {
        Database database = BdPruebas.nuevaBd(carpeta);
        SqliteUsuarioDAO usuarioDAO = new SqliteUsuarioDAO(database);
        SqlitePacienteDAO pacienteDAO = new SqlitePacienteDAO(database);
        citaDAO = new SqliteCitaDAO(database);
        servicio = new EstadisticasService(pacienteDAO, citaDAO, BdPruebas.reloj(MIERCOLES.atTime(12, 0)));

        admin = BdPruebas.usuario(usuarioDAO, "admin", Rol.ADMIN);
        fisio = BdPruebas.usuario(usuarioDAO, "fisio", Rol.FISIOTERAPEUTA);
        fisio2 = BdPruebas.usuario(usuarioDAO, "fisio2", Rol.FISIOTERAPEUTA);
        lucia = BdPruebas.paciente(pacienteDAO, "12345678Z", "Lucía", "Fernández Gil");
        javier = BdPruebas.paciente(pacienteDAO, "11111111H", "Javier", "Romero Navarro");
        general = BdPruebas.tratamiento(new SqliteTratamientoDAO(database), "General", 45, 35);

        crear(fisio, lucia, MIERCOLES.atTime(10, 0), EstadoCita.REALIZADA);
        crear(fisio, javier, MIERCOLES.atTime(16, 0), EstadoCita.PROGRAMADA);
        crear(fisio2, lucia, MIERCOLES.atTime(17, 0), EstadoCita.CANCELADA);
        crear(fisio2, javier, MIERCOLES.atTime(18, 0), EstadoCita.PROGRAMADA);
        crear(fisio, lucia, MIERCOLES.minusDays(2).atTime(10, 0), EstadoCita.NO_ASISTIDA);
        crear(fisio2, javier, MIERCOLES.minusDays(1).atTime(10, 0), EstadoCita.REALIZADA);
        crear(fisio, lucia, MIERCOLES.plusDays(5).atTime(10, 0), EstadoCita.PROGRAMADA);
        crear(fisio, lucia, MIERCOLES.plusDays(11).atTime(10, 0), EstadoCita.PROGRAMADA);
        crear(fisio, javier, MIERCOLES.minusDays(40).atTime(10, 0), EstadoCita.REALIZADA);
        Sesion.getInstance().iniciar(admin);
    }

    @AfterEach
    void tearDown() {
        BdPruebas.cerrar();
    }

    @Test
    void cifrasDeHoyYDeLaSemana() {
        assertEquals(2, servicio.pacientesTotales());
        assertEquals(3, servicio.citasHoy(null), "Las canceladas no cuentan");
        assertEquals(2, servicio.citasHoy(fisio.getId()));
        assertEquals(3, servicio.citasPendientesSemana(null));
        assertEquals(2, servicio.citasPendientesSemana(fisio.getId()));
    }

    @Test
    void tasaDeAsistenciaUltimos30Dias() {
        assertEquals(200.0 / 3, servicio.tasaAsistenciaUltimos30Dias(), 0.001);
        assertEquals(50.0, servicio.tasaAsistenciaUltimos30Dias(fisio.getId()), 0.001);
        Sesion.getInstance().iniciar(fisio);
        assertEquals(50.0, servicio.tasaAsistenciaUltimos30Dias(), 0.001, "Un fisio ve su propia tasa");
    }

    @Test
    void tasaDeAsistenciaSinDatosEsCero() {
        BdPruebas.cerrar();
        EstadisticasService vacio = new EstadisticasService(new SqlitePacienteDAO(BdPruebas.nuevaBd(carpeta.resolve("v"))),
                new SqliteCitaDAO(Database.getInstance()), BdPruebas.reloj(MIERCOLES.atTime(12, 0)));
        Sesion.getInstance().iniciar(admin);
        assertEquals(0.0, vacio.tasaAsistenciaUltimos30Dias());
    }

    @Test
    void citasPorDiaDeLaSemanaActualDeLunesASabado() {
        Map<DayOfWeek, Integer> porDia = servicio.citasPorDiaSemanaActual();
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), List.copyOf(porDia.keySet()));
        assertEquals(List.of(1, 1, 3, 0, 0, 0), List.copyOf(porDia.values()));

        Sesion.getInstance().iniciar(fisio);
        assertEquals(List.of(1, 0, 2, 0, 0, 0), List.copyOf(servicio.citasPorDiaSemanaActual().values()));
    }

    private void crear(Usuario f, Paciente p, LocalDateTime fechaHora, EstadoCita estado) {
        Cita c = BdPruebas.cita(p, f, general, fechaHora);
        c.setEstado(estado);
        citaDAO.insertar(c);
    }
}
