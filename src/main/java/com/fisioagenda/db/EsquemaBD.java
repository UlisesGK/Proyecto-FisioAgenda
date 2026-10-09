package com.fisioagenda.db;

import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.model.EstadoCita;
import com.fisioagenda.model.Rol;
import com.fisioagenda.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Creación del esquema de la base de datos y carga de los datos de demostración.
 * <p>
 * Los datos de demostración solo se cargan si la tabla {@code usuario} está vacía (primer arranque).
 * Las citas se generan relativas a la fecha actual, en horario de 9:00 a 20:00, de lunes a sábado
 * y sin solapes, de modo que cumplen todas las reglas de {@code CitaService}.
 */
public final class EsquemaBD {

    /** Sentencias DDL del esquema (5 tablas e índices), idempotentes gracias a {@code IF NOT EXISTS}. */
    private static final String[] DDL = {
        """
        CREATE TABLE IF NOT EXISTS usuario (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          username TEXT NOT NULL UNIQUE,
          nombre_completo TEXT NOT NULL,
          password_hash TEXT NOT NULL,
          salt TEXT NOT NULL,
          rol TEXT NOT NULL CHECK (rol IN ('ADMIN','FISIOTERAPEUTA','RECEPCION')),
          activo INTEGER NOT NULL DEFAULT 1,
          intentos_fallidos INTEGER NOT NULL DEFAULT 0,
          bloqueado_hasta TEXT
        )""",
        """
        CREATE TABLE IF NOT EXISTS paciente (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          dni TEXT NOT NULL UNIQUE,
          nombre TEXT NOT NULL,
          apellidos TEXT NOT NULL,
          telefono TEXT NOT NULL,
          email TEXT,
          fecha_nacimiento TEXT,
          observaciones TEXT,
          fecha_alta TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS tratamiento (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          nombre TEXT NOT NULL UNIQUE,
          descripcion TEXT,
          duracion_min INTEGER NOT NULL CHECK (duracion_min BETWEEN 10 AND 240),
          precio REAL NOT NULL CHECK (precio >= 0),
          activo INTEGER NOT NULL DEFAULT 1
        )""",
        """
        CREATE TABLE IF NOT EXISTS cita (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          paciente_id INTEGER NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
          fisioterapeuta_id INTEGER NOT NULL REFERENCES usuario(id),
          tratamiento_id INTEGER NOT NULL REFERENCES tratamiento(id),
          fecha_hora TEXT NOT NULL,
          estado TEXT NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA','REALIZADA','CANCELADA','NO_ASISTIDA')),
          notas TEXT
        )""",
        "CREATE INDEX IF NOT EXISTS idx_cita_fisio_fecha ON cita(fisioterapeuta_id, fecha_hora)",
        "CREATE INDEX IF NOT EXISTS idx_cita_paciente ON cita(paciente_id)",
        // Consultas por día o intervalo sin filtrar por fisioterapeuta (agenda de "Todos", comprobación de solapes,
        // estadísticas): sin este índice SQLite recorrería la tabla entera y ordenaría en una tabla temporal.
        "CREATE INDEX IF NOT EXISTS idx_cita_fecha ON cita(fecha_hora)",
        """
        CREATE TABLE IF NOT EXISTS nota_clinica (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          paciente_id INTEGER NOT NULL REFERENCES paciente(id) ON DELETE CASCADE,
          cita_id INTEGER REFERENCES cita(id) ON DELETE SET NULL,
          autor_id INTEGER NOT NULL REFERENCES usuario(id),
          fecha TEXT NOT NULL,
          contenido TEXT NOT NULL,
          escala_dolor INTEGER NOT NULL CHECK (escala_dolor BETWEEN 0 AND 10)
        )""",
        "CREATE INDEX IF NOT EXISTS idx_nota_paciente ON nota_clinica(paciente_id, fecha)",
        """
        CREATE TABLE IF NOT EXISTS dispositivo_recordado (
          token_hash TEXT PRIMARY KEY,
          usuario_id INTEGER NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
          expira TEXT NOT NULL
        )"""
    };

    // ---------------------------------------------------------------- datos de demostración

    private record UsuarioDemo(String username, String password, Rol rol, String nombre) { }

    private record TratamientoDemo(String nombre, String descripcion, int duracion, double precio) { }

    private record PacienteDemo(String dni, String nombre, String apellidos, String telefono, String email,
                                LocalDate nacimiento, String observaciones, int diasDesdeAlta) { }

    /**
     * Cita de demostración. {@code dia} es "R" (día de referencia: hoy, o el lunes si hoy es domingo),
     * "Fn" (n-ésimo día laborable posterior) o "Pn" (n-ésimo día laborable anterior a hoy).
     * Un estado {@code null} se calcula según la hora actual. {@code nota} es la nota clínica asociada, si la hay.
     */
    private record CitaDemo(String dia, String hora, int fisio, int paciente, int tratamiento,
                            EstadoCita estado, String notas, String nota, int eva) {
        CitaDemo(String dia, String hora, int fisio, int paciente, int tratamiento) {
            this(dia, hora, fisio, paciente, tratamiento, null, null, null, 0);
        }
    }

    private static final UsuarioDemo[] USUARIOS = {
        new UsuarioDemo("admin", "Admin1234!", Rol.ADMIN, "Laura Martín Ruiz"),
        new UsuarioDemo("fisio", "Fisio1234!", Rol.FISIOTERAPEUTA, "Carlos Pérez Gómez"),
        new UsuarioDemo("fisio2", "Fisio1234!", Rol.FISIOTERAPEUTA, "Marta Sánchez López"),
        new UsuarioDemo("recepcion", "Recep1234!", Rol.RECEPCION, "Ana Torres Díaz")
    };
    /** Índices en {@link #USUARIOS} de los dos fisioterapeutas. */
    private static final int CARLOS = 1;
    private static final int MARTA = 2;

    private static final TratamientoDemo[] TRATAMIENTOS = {
        new TratamientoDemo("Fisioterapia general",
                "Valoración y tratamiento de dolencias musculoesqueléticas.", 45, 35),
        new TratamientoDemo("Masaje descontracturante",
                "Masoterapia para liberar contracturas y tensión muscular.", 30, 30),
        new TratamientoDemo("Punción seca",
                "Tratamiento de puntos gatillo miofasciales con aguja de punción.", 30, 40),
        new TratamientoDemo("Rehabilitación postquirúrgica",
                "Recuperación funcional tras cirugía traumatológica u ortopédica.", 60, 45),
        new TratamientoDemo("Electroterapia",
                "Analgesia y estimulación muscular con corrientes (TENS, EMS).", 20, 20),
        new TratamientoDemo("Drenaje linfático",
                "Drenaje linfático manual para edemas y retención de líquidos.", 50, 40),
        new TratamientoDemo("Fisioterapia deportiva",
                "Prevención y recuperación de lesiones deportivas.", 45, 38)
    };
    private static final int GENERAL = 0;
    private static final int MASAJE = 1;
    private static final int PUNCION = 2;
    private static final int REHABILITACION = 3;
    private static final int ELECTRO = 4;
    private static final int DRENAJE = 5;
    private static final int DEPORTIVA = 6;

    private static final PacienteDemo[] PACIENTES = {
        new PacienteDemo("48291537R", "Lucía", "Fernández Gil", "612345781", "lucia.fernandez@example.com",
                LocalDate.of(1985, 3, 14), "Lumbalgia mecánica. Trabaja muchas horas sentada.", 120),
        new PacienteDemo("53104862R", "Javier", "Romero Navarro", "634128957", "javier.romero@example.com",
                LocalDate.of(1994, 7, 22), "Corredor popular. Esguince de tobillo izquierdo.", 60),
        new PacienteDemo("71956240G", "Elena", "Castro Molina", "655902314", "elena.castro@example.com",
                LocalDate.of(1990, 11, 5), "Ligamentoplastia de LCA en rodilla derecha. Juega al pádel.", 45),
        new PacienteDemo("25847319B", "Miguel Ángel", "Ortega Ruiz", "667213498", "miguelangel.ortega@example.com",
                LocalDate.of(1978, 1, 30), "Cervicalgia crónica con cefaleas tensionales ocasionales.", 200),
        new PacienteDemo("39418756E", "Carmen", "Delgado Vázquez", "678450126", "carmen.delgado@example.com",
                LocalDate.of(1969, 9, 18), "Tendinopatía del supraespinoso en hombro derecho.", 90),
        new PacienteDemo("44629183Y", "David", "Herrera Iglesias", "689314572", "david.herrera@example.com",
                LocalDate.of(1982, 5, 9), "Fascitis plantar bilateral.", 30),
        new PacienteDemo("16738294K", "Sara", "Jiménez Moreno", "610987234", "sara.jimenez@example.com",
                LocalDate.of(1997, 12, 2), "Edema de tobillo tras inmovilización por fractura de peroné.", 40),
        new PacienteDemo("50372916H", "Pablo", "Muñoz Serrano", "622781345", "pablo.munoz@example.com",
                LocalDate.of(1988, 8, 27), "Epicondilalgia derecha (codo de tenista). Alérgico al látex.", 75),
        new PacienteDemo("X4827391J", "Giulia", "Bianchi", "645230918", "giulia.bianchi@example.com",
                LocalDate.of(1993, 4, 16), "Dolor dorsal por postura mantenida. Derivada por su médico.", 3),
        new PacienteDemo("29563871Q", "Alejandro", "Suárez Rubio", "691456720", "alejandro.suarez@example.com",
                LocalDate.of(2001, 2, 11), "Rotura fibrilar de isquiotibiales jugando al fútbol.", 2),
        new PacienteDemo("62840157V", "Natalia", "Vega Prieto", "603874129", "natalia.vega@example.com",
                LocalDate.of(1986, 6, 25), "Contractura de trapecio superior por teletrabajo.", 50),
        new PacienteDemo("34915628H", "Raúl", "Domínguez Cano", "676092384", null,
                LocalDate.of(1954, 10, 3), "Prótesis total de cadera izquierda. Camina con bastón.", 150)
    };
    private static final int LUCIA = 0;
    private static final int JAVIER = 1;
    private static final int ELENA = 2;
    private static final int MIGUEL = 3;
    private static final int CARMEN = 4;
    private static final int DAVID = 5;
    private static final int SARA = 6;
    private static final int PABLO = 7;
    private static final int GIULIA = 8;
    private static final int ALEJANDRO = 9;
    private static final int NATALIA = 10;
    private static final int RAUL = 11;

    private static final CitaDemo[] CITAS = {
        // Día de referencia ("hoy"): agenda de ambos fisioterapeutas.
        new CitaDemo("R", "09:00", CARLOS, LUCIA, GENERAL),
        new CitaDemo("R", "10:00", CARLOS, MIGUEL, MASAJE),
        new CitaDemo("R", "11:00", CARLOS, ELENA, REHABILITACION, null,
                "Traer el último informe del traumatólogo.", null, 0),
        new CitaDemo("R", "17:00", CARLOS, DAVID, GENERAL),
        new CitaDemo("R", "09:30", MARTA, JAVIER, DEPORTIVA),
        new CitaDemo("R", "10:30", MARTA, CARMEN, GENERAL),
        new CitaDemo("R", "12:00", MARTA, SARA, DRENAJE),
        new CitaDemo("R", "18:00", MARTA, RAUL, REHABILITACION),
        // Próximos días.
        new CitaDemo("F1", "09:30", CARLOS, PABLO, PUNCION),
        new CitaDemo("F1", "16:00", CARLOS, LUCIA, GENERAL),
        new CitaDemo("F1", "10:00", MARTA, NATALIA, MASAJE),
        new CitaDemo("F2", "10:00", CARLOS, MIGUEL, GENERAL),
        new CitaDemo("F2", "16:00", MARTA, GIULIA, GENERAL, null,
                "Primera visita. Derivada por su médico de cabecera.", null, 0),
        new CitaDemo("F2", "18:00", MARTA, ALEJANDRO, DEPORTIVA),
        new CitaDemo("F4", "09:00", MARTA, RAUL, REHABILITACION),
        new CitaDemo("F4", "18:00", CARLOS, DAVID, ELECTRO),
        // Días pasados, con su historial clínico.
        new CitaDemo("P1", "09:00", CARLOS, LUCIA, GENERAL, EstadoCita.REALIZADA, null,
                "Buena evolución; tolera mejor estar sentada. Se inicia fortalecimiento de core "
                + "(plancha y puente de glúteo).", 6),
        new CitaDemo("P1", "10:00", CARLOS, ELENA, REHABILITACION, EstadoCita.REALIZADA, null,
                "Flexión de rodilla de 120°. Marcha sin muletas. Ejercicios en cadena cinética cerrada "
                + "y bicicleta estática 10 minutos.", 5),
        new CitaDemo("P1", "11:00", MARTA, JAVIER, DEPORTIVA, EstadoCita.REALIZADA, null,
                "Sin edema. Propiocepción en plato inestable. Puede retomar la carrera suave en llano.", 3),
        new CitaDemo("P1", "12:30", MARTA, CARMEN, GENERAL, EstadoCita.NO_ASISTIDA, null, null, 0),
        new CitaDemo("P2", "16:00", CARLOS, PABLO, PUNCION, EstadoCita.REALIZADA, null,
                "Epicondilalgia derecha. Punción seca en extensor radial corto del carpo. "
                + "Ejercicio excéntrico con mancuerna de 1 kg.", 6),
        new CitaDemo("P2", "17:00", MARTA, SARA, DRENAJE, EstadoCita.REALIZADA, null,
                "Edema residual de tobillo tras retirar la inmovilización. Drenaje linfático manual; "
                + "se recomienda elevar la pierna y usar media de compresión.", 4),
        new CitaDemo("P2", "18:00", CARLOS, MIGUEL, MASAJE, EstadoCita.CANCELADA,
                "Avisa por teléfono de que no puede venir por trabajo.", null, 0),
        new CitaDemo("P3", "09:00", CARLOS, LUCIA, GENERAL, EstadoCita.REALIZADA, null,
                "Mejoría parcial. Persiste dolor al flexionar el tronco. Liberación miofascial "
                + "y estiramientos de isquiotibiales.", 7),
        new CitaDemo("P3", "10:30", CARLOS, ELENA, REHABILITACION, EstadoCita.REALIZADA, null,
                "Flexión de rodilla de 105°. Activación de cuádriceps y propiocepción en descarga parcial.", 7),
        new CitaDemo("P3", "10:00", MARTA, RAUL, REHABILITACION, EstadoCita.REALIZADA, null,
                "Rehabilitación tras prótesis total de cadera izquierda (8 semanas). Marcha con un bastón. "
                + "Fortalecimiento de glúteo medio.", 5),
        new CitaDemo("P5", "11:00", CARLOS, LUCIA, GENERAL, EstadoCita.REALIZADA, null,
                "Primera sesión. Lumbalgia mecánica de tres semanas de evolución con contractura paravertebral "
                + "bilateral. Termoterapia y masoterapia; ejercicios de movilidad lumbar para casa.", 8),
        new CitaDemo("P5", "12:00", MARTA, JAVIER, DEPORTIVA, EstadoCita.REALIZADA, null,
                "Esguince de grado II del ligamento lateral externo del tobillo izquierdo. Edema moderado. "
                + "Drenaje, vendaje funcional y movilidad activa.", 6),
        new CitaDemo("P5", "16:00", MARTA, NATALIA, MASAJE, EstadoCita.NO_ASISTIDA, null, null, 0),
        new CitaDemo("P8", "10:00", CARLOS, ELENA, REHABILITACION, EstadoCita.REALIZADA, null,
                "Valoración a las 4 semanas de la ligamentoplastia de LCA. Flexión de 90°, extensión completa "
                + "y derrame leve.", 8),
        new CitaDemo("P8", "17:00", MARTA, CARMEN, GENERAL, EstadoCita.REALIZADA, null,
                "Dolor al elevar el brazo por encima de 90°. Ejercicios isométricos de manguito rotador "
                + "y terapia manual.", 7)
    };

    private EsquemaBD() {
    }

    /**
     * Crea las tablas si no existen y, si no hay ningún usuario, carga los datos de demostración
     * relativos a la fecha y hora actuales.
     *
     * @throws DataAccessException si falla alguna sentencia
     */
    public static void inicializar() {
        inicializar(Clock.systemDefaultZone());
    }

    /**
     * Igual que {@link #inicializar()} pero tomando la fecha y hora actuales del reloj indicado
     * (permite comprobar los datos de demostración en cualquier día de la semana).
     */
    public static void inicializar(Clock reloj) {
        Connection con = Database.getInstance().getConexion();
        crearTablas(con);
        if (contarUsuarios(con) == 0) {
            cargarDatosDemo(con, LocalDateTime.now(reloj));
        }
    }

    /** Crea únicamente las tablas e índices, sin datos (útil para las pruebas). */
    public static void crearTablas() {
        crearTablas(Database.getInstance().getConexion());
    }

    /**
     * Día en el que se colocan las citas "de hoy" de la demostración: hoy, salvo que sea domingo
     * (la clínica no abre), en cuyo caso se usa el lunes siguiente, el día laborable más cercano en el futuro.
     */
    public static LocalDate diaDeReferencia(LocalDate hoy) {
        return hoy.getDayOfWeek() == DayOfWeek.SUNDAY ? hoy.plusDays(1) : hoy;
    }

    private static void crearTablas(Connection con) {
        try (Statement st = con.createStatement()) {
            for (String sentencia : DDL) {
                st.execute(sentencia);
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo crear el esquema de la base de datos", e);
        }
    }

    private static int contarUsuarios(Connection con) {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuario")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo consultar la tabla de usuarios", e);
        }
    }

    /** Inserta todos los datos de demostración en una única transacción. */
    private static void cargarDatosDemo(Connection con, LocalDateTime ahora) {
        try {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                int[] usuarios = insertarUsuarios(con);
                int[] tratamientos = insertarTratamientos(con);
                int[] pacientes = insertarPacientes(con, ahora.toLocalDate());
                insertarCitasYNotas(con, ahora, usuarios, tratamientos, pacientes);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron cargar los datos de demostración", e);
        }
    }

    private static int[] insertarUsuarios(Connection con) throws SQLException {
        String sql = "INSERT INTO usuario (username, nombre_completo, password_hash, salt, rol, activo) "
                + "VALUES (?, ?, ?, ?, ?, 1)";
        int[] ids = new int[USUARIOS.length];
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < USUARIOS.length; i++) {
                UsuarioDemo u = USUARIOS[i];
                String sal = PasswordHasher.generarSal();
                ps.setString(1, u.username());
                ps.setString(2, u.nombre());
                ps.setString(3, PasswordHasher.hash(u.password().toCharArray(), sal));
                ps.setString(4, sal);
                ps.setString(5, u.rol().name());
                ids[i] = ejecutarInsercion(ps);
            }
        }
        return ids;
    }

    private static int[] insertarTratamientos(Connection con) throws SQLException {
        String sql = "INSERT INTO tratamiento (nombre, descripcion, duracion_min, precio, activo) VALUES (?, ?, ?, ?, 1)";
        int[] ids = new int[TRATAMIENTOS.length];
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < TRATAMIENTOS.length; i++) {
                TratamientoDemo t = TRATAMIENTOS[i];
                ps.setString(1, t.nombre());
                ps.setString(2, t.descripcion());
                ps.setInt(3, t.duracion());
                ps.setDouble(4, t.precio());
                ids[i] = ejecutarInsercion(ps);
            }
        }
        return ids;
    }

    private static int[] insertarPacientes(Connection con, LocalDate hoy) throws SQLException {
        String sql = "INSERT INTO paciente (dni, nombre, apellidos, telefono, email, fecha_nacimiento, observaciones, "
                + "fecha_alta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int[] ids = new int[PACIENTES.length];
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < PACIENTES.length; i++) {
                PacienteDemo p = PACIENTES[i];
                ps.setString(1, p.dni());
                ps.setString(2, p.nombre());
                ps.setString(3, p.apellidos());
                ps.setString(4, p.telefono());
                ps.setString(5, p.email());
                ps.setString(6, FechasBD.texto(p.nacimiento()));
                ps.setString(7, p.observaciones());
                ps.setString(8, FechasBD.texto(hoy.minusDays(p.diasDesdeAlta())));
                ids[i] = ejecutarInsercion(ps);
            }
        }
        return ids;
    }

    private static void insertarCitasYNotas(Connection con, LocalDateTime ahora, int[] usuarios,
                                            int[] tratamientos, int[] pacientes) throws SQLException {
        LocalDate hoy = ahora.toLocalDate();
        LocalDate referencia = diaDeReferencia(hoy);
        List<LocalDate> futuros = diasLaborables(referencia, 1, 6);
        List<LocalDate> pasados = diasLaborables(hoy, -1, 10);

        String sqlCita = "INSERT INTO cita (paciente_id, fisioterapeuta_id, tratamiento_id, fecha_hora, estado, notas) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        String sqlNota = "INSERT INTO nota_clinica (paciente_id, cita_id, autor_id, fecha, contenido, escala_dolor) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement psCita = con.prepareStatement(sqlCita, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement psNota = con.prepareStatement(sqlNota)) {
            for (CitaDemo c : CITAS) {
                LocalDate dia = resolverDia(c.dia(), referencia, futuros, pasados);
                LocalDateTime inicio = LocalDateTime.of(dia, LocalTime.parse(c.hora()));
                LocalDateTime fin = inicio.plusMinutes(TRATAMIENTOS[c.tratamiento()].duracion());
                EstadoCita estado = c.estado() != null ? c.estado()
                        : (fin.isAfter(ahora) ? EstadoCita.PROGRAMADA : EstadoCita.REALIZADA);

                psCita.setInt(1, pacientes[c.paciente()]);
                psCita.setInt(2, usuarios[c.fisio()]);
                psCita.setInt(3, tratamientos[c.tratamiento()]);
                psCita.setString(4, FechasBD.textoMinutos(inicio));
                psCita.setString(5, estado.name());
                if (c.notas() == null) {
                    psCita.setNull(6, Types.VARCHAR);
                } else {
                    psCita.setString(6, c.notas());
                }
                int citaId = ejecutarInsercion(psCita);

                if (c.nota() != null) {
                    psNota.setInt(1, pacientes[c.paciente()]);
                    psNota.setInt(2, citaId);
                    psNota.setInt(3, usuarios[c.fisio()]);
                    psNota.setString(4, FechasBD.textoMinutos(fin));
                    psNota.setString(5, c.nota());
                    psNota.setInt(6, c.eva());
                    psNota.executeUpdate();
                }
            }
        }
    }

    /** Devuelve {@code cantidad} días laborables (lunes a sábado) a partir de {@code origen}, sin incluirlo. */
    private static List<LocalDate> diasLaborables(LocalDate origen, int paso, int cantidad) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate dia = origen;
        while (dias.size() < cantidad) {
            dia = dia.plusDays(paso);
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dias.add(dia);
            }
        }
        return dias;
    }

    private static LocalDate resolverDia(String clave, LocalDate referencia, List<LocalDate> futuros,
                                         List<LocalDate> pasados) {
        if ("R".equals(clave)) {
            return referencia;
        }
        int indice = Integer.parseInt(clave.substring(1)) - 1;
        return clave.charAt(0) == 'F' ? futuros.get(indice) : pasados.get(indice);
    }

    private static int ejecutarInsercion(PreparedStatement ps) throws SQLException {
        ps.executeUpdate();
        try (ResultSet claves = ps.getGeneratedKeys()) {
            if (!claves.next()) {
                throw new SQLException("No se obtuvo el identificador generado");
            }
            return claves.getInt(1);
        }
    }
}
