package com.fisioagenda.service;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.NotaClinicaDAO;
import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.dao.TratamientoDAO;
import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.dao.sqlite.SqliteCitaDAO;
import com.fisioagenda.dao.sqlite.SqliteNotaClinicaDAO;
import com.fisioagenda.dao.sqlite.SqlitePacienteDAO;
import com.fisioagenda.dao.sqlite.SqliteTratamientoDAO;
import com.fisioagenda.dao.sqlite.SqliteUsuarioDAO;
import com.fisioagenda.db.Database;

import java.time.Clock;

/**
 * Registro central de servicios (singleton perezoso). La primera vez que se pide un servicio se construyen
 * los DAO SQLite sobre {@link Database#getInstance()} y todos los servicios, una sola vez.
 * La interfaz obtiene aquí los servicios en lugar de crearlos.
 */
public final class ServiceLocator {

    private static ServiceLocator instancia;

    private final AuthService auth;
    private final PacienteService pacientes;
    private final CitaService citas;
    private final TratamientoService tratamientos;
    private final UsuarioService usuarios;
    private final HistorialService historial;
    private final EstadisticasService estadisticas;

    private ServiceLocator() {
        Database database = Database.getInstance();
        UsuarioDAO usuarioDAO = new SqliteUsuarioDAO(database);
        PacienteDAO pacienteDAO = new SqlitePacienteDAO(database);
        TratamientoDAO tratamientoDAO = new SqliteTratamientoDAO(database);
        CitaDAO citaDAO = new SqliteCitaDAO(database);
        NotaClinicaDAO notaDAO = new SqliteNotaClinicaDAO(database);

        auth = new AuthService(usuarioDAO, Clock.systemDefaultZone());
        pacientes = new PacienteService(pacienteDAO);
        citas = new CitaService(citaDAO, pacienteDAO, usuarioDAO, tratamientoDAO);
        tratamientos = new TratamientoService(tratamientoDAO, citaDAO);
        usuarios = new UsuarioService(usuarioDAO);
        historial = new HistorialService(notaDAO, pacienteDAO, citaDAO);
        estadisticas = new EstadisticasService(pacienteDAO, citaDAO);
    }

    private static synchronized ServiceLocator get() {
        if (instancia == null) {
            instancia = new ServiceLocator();
        }
        return instancia;
    }

    /** Descarta los servicios creados; la próxima petición los reconstruye (útil si cambia la base de datos). */
    public static synchronized void reiniciar() {
        instancia = null;
    }

    /** Servicio de autenticación. */
    public static AuthService auth() {
        return get().auth;
    }

    /** Servicio de pacientes. */
    public static PacienteService pacientes() {
        return get().pacientes;
    }

    /** Servicio de citas y agenda. */
    public static CitaService citas() {
        return get().citas;
    }

    /** Servicio del catálogo de tratamientos. */
    public static TratamientoService tratamientos() {
        return get().tratamientos;
    }

    /** Servicio de gestión de usuarios. */
    public static UsuarioService usuarios() {
        return get().usuarios;
    }

    /** Servicio del historial clínico. */
    public static HistorialService historial() {
        return get().historial;
    }

    /** Servicio de estadísticas del panel de inicio. */
    public static EstadisticasService estadisticas() {
        return get().estadisticas;
    }
}
