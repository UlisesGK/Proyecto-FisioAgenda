package com.fisioagenda.dao.sqlite;

import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.dao.NotaClinicaDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.FechasBD;
import com.fisioagenda.model.NotaClinica;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Implementación SQLite de {@link NotaClinicaDAO}. El autor de cada nota se carga con un JOIN. */
public class SqliteNotaClinicaDAO implements NotaClinicaDAO {

    private static final String SELECT_NOTAS = """
            SELECT n.id, n.paciente_id, n.cita_id, n.fecha, n.contenido, n.escala_dolor,
                   u.id AS u_id, u.username AS u_username, u.nombre_completo AS u_nombre_completo,
                   u.rol AS u_rol, u.activo AS u_activo
            FROM nota_clinica n
            JOIN usuario u ON u.id = n.autor_id
            WHERE n.paciente_id = ?
            ORDER BY n.fecha DESC, n.id DESC
            """;

    private final Database database;

    /** Crea el DAO sobre la base de datos indicada. */
    public SqliteNotaClinicaDAO(Database database) {
        this.database = database;
    }

    /** Crea el DAO sobre la base de datos por defecto ({@link Database#getInstance()}). */
    public SqliteNotaClinicaDAO() {
        this(Database.getInstance());
    }

    @Override
    public List<NotaClinica> listarPorPaciente(int pacienteId) {
        try (PreparedStatement ps = database.getConexion().prepareStatement(SELECT_NOTAS)) {
            ps.setInt(1, pacienteId);
            try (ResultSet rs = ps.executeQuery()) {
                List<NotaClinica> notas = new ArrayList<>();
                while (rs.next()) {
                    notas.add(mapear(rs));
                }
                return notas;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo consultar el historial clínico", e);
        }
    }

    @Override
    public NotaClinica insertar(NotaClinica nota) {
        String sql = "INSERT INTO nota_clinica (paciente_id, cita_id, autor_id, fecha, contenido, escala_dolor) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, nota.getPacienteId());
            Mapeos.enteroOpcional(ps, 2, nota.getCitaId());
            ps.setInt(3, nota.getAutor().getId());
            ps.setString(4, FechasBD.textoMinutos(nota.getFecha()));
            ps.setString(5, nota.getContenido());
            ps.setInt(6, nota.getEscalaDolor());
            nota.setId(Mapeos.insertarYObtenerId(ps));
            return nota;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo guardar la nota clínica", e);
        }
    }

    private static NotaClinica mapear(ResultSet rs) throws SQLException {
        NotaClinica n = new NotaClinica();
        n.setId(rs.getInt("id"));
        n.setPacienteId(rs.getInt("paciente_id"));
        n.setCitaId(Mapeos.enteroOpcional(rs, "cita_id"));
        n.setFecha(FechasBD.fechaHora(rs.getString("fecha")));
        n.setContenido(rs.getString("contenido"));
        n.setEscalaDolor(rs.getInt("escala_dolor"));
        n.setAutor(Mapeos.usuarioBasico(rs, "u_"));
        return n;
    }
}
