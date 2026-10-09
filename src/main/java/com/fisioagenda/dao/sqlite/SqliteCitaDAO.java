package com.fisioagenda.dao.sqlite;

import com.fisioagenda.dao.CitaDAO;
import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.FechasBD;
import com.fisioagenda.model.Cita;
import com.fisioagenda.model.EstadoCita;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación SQLite de {@link CitaDAO}. Todas las consultas cargan la cita junto a su paciente,
 * fisioterapeuta y tratamiento con un único JOIN (sin consultas adicionales por fila).
 */
public class SqliteCitaDAO implements CitaDAO {

    private static final String SELECT_CITAS = """
            SELECT c.id, c.fecha_hora, c.estado, c.notas,
                   p.id AS p_id, p.dni AS p_dni, p.nombre AS p_nombre, p.apellidos AS p_apellidos,
                   p.telefono AS p_telefono, p.email AS p_email, p.fecha_nacimiento AS p_fecha_nacimiento,
                   p.observaciones AS p_observaciones, p.fecha_alta AS p_fecha_alta,
                   u.id AS u_id, u.username AS u_username, u.nombre_completo AS u_nombre_completo,
                   u.rol AS u_rol, u.activo AS u_activo,
                   t.id AS t_id, t.nombre AS t_nombre, t.descripcion AS t_descripcion,
                   t.duracion_min AS t_duracion_min, t.precio AS t_precio, t.activo AS t_activo
            FROM cita c
            JOIN paciente p ON p.id = c.paciente_id
            JOIN usuario u ON u.id = c.fisioterapeuta_id
            JOIN tratamiento t ON t.id = c.tratamiento_id
            """;
    private static final String ORDEN_CRONOLOGICO = " ORDER BY c.fecha_hora, u.nombre_completo";

    private final Database database;

    /** Crea el DAO sobre la base de datos indicada. */
    public SqliteCitaDAO(Database database) {
        this.database = database;
    }

    /** Crea el DAO sobre la base de datos por defecto ({@link Database#getInstance()}). */
    public SqliteCitaDAO() {
        this(Database.getInstance());
    }

    @Override
    public Optional<Cita> buscarPorId(int id) {
        return consultar(SELECT_CITAS + " WHERE c.id = ?", id).stream().findFirst();
    }

    @Override
    public List<Cita> listarPorDia(LocalDate dia, Integer fisioterapeutaId) {
        return listarEntre(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay(), fisioterapeutaId);
    }

    @Override
    public List<Cita> listarEntre(LocalDateTime desde, LocalDateTime hasta, Integer fisioterapeutaId) {
        String sql = SELECT_CITAS + " WHERE c.fecha_hora >= ? AND c.fecha_hora < ?";
        if (fisioterapeutaId == null) {
            return consultar(sql + ORDEN_CRONOLOGICO, FechasBD.textoPreciso(desde), FechasBD.textoPreciso(hasta));
        }
        return consultar(sql + " AND c.fisioterapeuta_id = ?" + ORDEN_CRONOLOGICO,
                FechasBD.textoPreciso(desde), FechasBD.textoPreciso(hasta), fisioterapeutaId);
    }

    /** {@inheritDoc} Ordenadas de la más reciente a la más antigua. */
    @Override
    public List<Cita> listarPorPaciente(int pacienteId) {
        return consultar(SELECT_CITAS + " WHERE c.paciente_id = ? ORDER BY c.fecha_hora DESC", pacienteId);
    }

    @Override
    public Cita insertar(Cita cita) {
        String sql = "INSERT INTO cita (paciente_id, fisioterapeuta_id, tratamiento_id, fecha_hora, estado, notas) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, cita);
            cita.setId(Mapeos.insertarYObtenerId(ps));
            return cita;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo guardar la cita", e);
        }
    }

    @Override
    public void actualizar(Cita cita) {
        String sql = "UPDATE cita SET paciente_id = ?, fisioterapeuta_id = ?, tratamiento_id = ?, fecha_hora = ?, "
                + "estado = ?, notas = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            asignarCampos(ps, cita);
            ps.setInt(7, cita.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo actualizar la cita", e);
        }
    }

    @Override
    public void actualizarEstado(int citaId, EstadoCita estado) {
        try (PreparedStatement ps = database.getConexion().prepareStatement("UPDATE cita SET estado = ? WHERE id = ?")) {
            ps.setString(1, estado.name());
            ps.setInt(2, citaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo cambiar el estado de la cita", e);
        }
    }

    @Override
    public void eliminar(int id) {
        try (PreparedStatement ps = database.getConexion().prepareStatement("DELETE FROM cita WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo eliminar la cita", e);
        }
    }

    @Override
    public int contarPorEstadoEntre(EstadoCita estado, LocalDateTime desde, LocalDateTime hasta) {
        String sql = "SELECT COUNT(*) FROM cita WHERE estado = ? AND fecha_hora >= ? AND fecha_hora < ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setString(2, FechasBD.textoPreciso(desde));
            ps.setString(3, FechasBD.textoPreciso(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron contar las citas", e);
        }
    }

    private static void asignarCampos(PreparedStatement ps, Cita c) throws SQLException {
        ps.setInt(1, c.getPaciente().getId());
        ps.setInt(2, c.getFisioterapeuta().getId());
        ps.setInt(3, c.getTratamiento().getId());
        ps.setString(4, FechasBD.textoMinutos(c.getFechaHora()));
        ps.setString(5, c.getEstado().name());
        Mapeos.textoOpcional(ps, 6, c.getNotas());
    }

    private List<Cita> consultar(String sql, Object... parametros) {
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Cita> citas = new ArrayList<>();
                while (rs.next()) {
                    citas.add(mapear(rs));
                }
                return citas;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron consultar las citas", e);
        }
    }

    private static Cita mapear(ResultSet rs) throws SQLException {
        Cita c = new Cita();
        c.setId(rs.getInt("id"));
        c.setFechaHora(FechasBD.fechaHora(rs.getString("fecha_hora")));
        c.setEstado(EstadoCita.valueOf(rs.getString("estado")));
        c.setNotas(rs.getString("notas"));
        c.setPaciente(Mapeos.paciente(rs, "p_"));
        c.setFisioterapeuta(Mapeos.usuarioBasico(rs, "u_"));
        c.setTratamiento(Mapeos.tratamiento(rs, "t_"));
        return c;
    }
}
