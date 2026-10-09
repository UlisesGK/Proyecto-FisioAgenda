package com.fisioagenda;

import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.dao.TratamientoDAO;
import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.EsquemaBD;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;
import com.fisioagenda.security.PasswordHasher;
import com.fisioagenda.security.Sesion;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Utilidades compartidas por las pruebas: base de datos SQLite en un fichero temporal y
 * creación rápida de usuarios, pacientes, tratamientos y citas de ejemplo.
 */
public final class BdPruebas {

    /** Contraseña de todos los usuarios creados con {@link #usuario}. */
    public static final String PASSWORD = "Clave1234!";

    /** Lunes laborable en el futuro usado como día de referencia en las pruebas de agenda. */
    public static final LocalDate LUNES = LocalDate.of(2030, 1, 7);

    private static String sal;
    private static String hash;

    private BdPruebas() {
    }

    /** Configura {@link Database} sobre un fichero nuevo dentro de {@code carpeta} y crea las tablas vacías. */
    public static Database nuevaBd(Path carpeta) {
        Database.configurar(carpeta.resolve("prueba.db").toString());
        EsquemaBD.crearTablas();
        return Database.getInstance();
    }

    /** Cierra la sesión y la conexión (necesario para que se pueda borrar la carpeta temporal en Windows). */
    public static void cerrar() {
        Sesion.getInstance().cerrar();
        Database.configurar(null);
    }

    /** Reloj fijo en la fecha y hora indicadas (zona horaria del sistema). */
    public static Clock reloj(LocalDateTime ahora) {
        ZoneId zona = ZoneId.systemDefault();
        return Clock.fixed(ahora.atZone(zona).toInstant(), zona);
    }

    /** Inserta un usuario activo con la contraseña {@link #PASSWORD} (el hash se calcula una sola vez). */
    public static synchronized Usuario usuario(UsuarioDAO dao, String username, Rol rol) {
        if (hash == null) {
            sal = PasswordHasher.generarSal();
            hash = PasswordHasher.hash(PASSWORD.toCharArray(), sal);
        }
        Usuario u = new Usuario(0, username, "Nombre de " + username, rol);
        u.setSalt(sal);
        u.setPasswordHash(hash);
        return dao.insertar(u);
    }

    /** Inserta un paciente válido con el DNI indicado. */
    public static Paciente paciente(PacienteDAO dao, String dni, String nombre, String apellidos) {
        Paciente p = new Paciente();
        p.setDni(dni);
        p.setNombre(nombre);
        p.setApellidos(apellidos);
        p.setTelefono("612345678");
        p.setEmail(nombre.toLowerCase() + "@example.com");
        p.setFechaNacimiento(LocalDate.of(1990, 5, 20));
        p.setFechaAlta(LocalDate.of(2025, 1, 15));
        return dao.insertar(p);
    }

    /** Inserta un tratamiento activo. */
    public static Tratamiento tratamiento(TratamientoDAO dao, String nombre, int duracion, double precio) {
        return dao.insertar(new Tratamiento(0, nombre, duracion, precio));
    }

    /** Crea (sin guardar) una cita nueva con los datos indicados. */
    public static Cita cita(Paciente p, Usuario fisio, Tratamiento t, LocalDateTime fechaHora) {
        Cita c = new Cita();
        c.setPaciente(p);
        c.setFisioterapeuta(fisio);
        c.setTratamiento(t);
        c.setFechaHora(fechaHora);
        return c;
    }
}
