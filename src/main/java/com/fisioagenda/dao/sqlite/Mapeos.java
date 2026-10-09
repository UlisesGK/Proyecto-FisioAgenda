package com.fisioagenda.dao.sqlite;

import com.fisioagenda.db.FechasBD;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Tratamiento;
import com.fisioagenda.model.Usuario;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Conversión de filas de {@link ResultSet} a objetos del modelo, compartida por los DAO SQLite.
 * Cada método recibe un prefijo de columna para poder reutilizarse en consultas con JOIN
 * (por ejemplo {@code p_nombre}); en consultas sobre una sola tabla el prefijo es vacío.
 */
final class Mapeos {

    private Mapeos() {
    }

    static Paciente paciente(ResultSet rs, String prefijo) throws SQLException {
        Paciente p = new Paciente();
        p.setId(rs.getInt(prefijo + "id"));
        p.setDni(rs.getString(prefijo + "dni"));
        p.setNombre(rs.getString(prefijo + "nombre"));
        p.setApellidos(rs.getString(prefijo + "apellidos"));
        p.setTelefono(rs.getString(prefijo + "telefono"));
        p.setEmail(rs.getString(prefijo + "email"));
        p.setFechaNacimiento(FechasBD.fecha(rs.getString(prefijo + "fecha_nacimiento")));
        p.setObservaciones(rs.getString(prefijo + "observaciones"));
        p.setFechaAlta(FechasBD.fecha(rs.getString(prefijo + "fecha_alta")));
        return p;
    }

    static Tratamiento tratamiento(ResultSet rs, String prefijo) throws SQLException {
        Tratamiento t = new Tratamiento();
        t.setId(rs.getInt(prefijo + "id"));
        t.setNombre(rs.getString(prefijo + "nombre"));
        t.setDescripcion(rs.getString(prefijo + "descripcion"));
        t.setDuracionMinutos(rs.getInt(prefijo + "duracion_min"));
        t.setPrecio(rs.getDouble(prefijo + "precio"));
        t.setActivo(rs.getInt(prefijo + "activo") == 1);
        return t;
    }

    /** Datos públicos del usuario (sin credenciales ni control de acceso), para citas y notas. */
    static Usuario usuarioBasico(ResultSet rs, String prefijo) throws SQLException {
        Usuario u = new Usuario(rs.getInt(prefijo + "id"), rs.getString(prefijo + "username"),
                rs.getString(prefijo + "nombre_completo"), Rol.valueOf(rs.getString(prefijo + "rol")));
        u.setActivo(rs.getInt(prefijo + "activo") == 1);
        return u;
    }

    /** Lee un entero que puede ser NULL en la base de datos. */
    static Integer enteroOpcional(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    /** Asigna un texto que puede ser nulo a un parámetro. */
    static void textoOpcional(PreparedStatement ps, int indice, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.VARCHAR);
        } else {
            ps.setString(indice, valor);
        }
    }

    /** Asigna un entero que puede ser nulo a un parámetro. */
    static void enteroOpcional(PreparedStatement ps, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(indice, Types.INTEGER);
        } else {
            ps.setInt(indice, valor);
        }
    }

    /** Ejecuta un INSERT preparado con {@link Statement#RETURN_GENERATED_KEYS} y devuelve el id generado. */
    static int insertarYObtenerId(PreparedStatement ps) throws SQLException {
        ps.executeUpdate();
        try (ResultSet claves = ps.getGeneratedKeys()) {
            if (!claves.next()) {
                throw new SQLException("La base de datos no devolvió el identificador generado");
            }
            return claves.getInt(1);
        }
    }
}
