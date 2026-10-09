package com.fisioagenda.dao.sqlite;

import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.dao.TratamientoDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.model.Tratamiento;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación SQLite de {@link TratamientoDAO}. */
public class SqliteTratamientoDAO implements TratamientoDAO {

    private static final String COLUMNAS = "SELECT id, nombre, descripcion, duracion_min, precio, activo FROM tratamiento";
    private static final String ORDEN = " ORDER BY nombre COLLATE NOCASE";

    private final Database database;

    /** Crea el DAO sobre la base de datos indicada. */
    public SqliteTratamientoDAO(Database database) {
        this.database = database;
    }

    /** Crea el DAO sobre la base de datos por defecto ({@link Database#getInstance()}). */
    public SqliteTratamientoDAO() {
        this(Database.getInstance());
    }

    @Override
    public Optional<Tratamiento> buscarPorId(int id) {
        return consultar(COLUMNAS + " WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<Tratamiento> listarTodos() {
        return consultar(COLUMNAS + ORDEN, null);
    }

    @Override
    public List<Tratamiento> listarActivos() {
        return consultar(COLUMNAS + " WHERE activo = 1" + ORDEN, null);
    }

    @Override
    public Tratamiento insertar(Tratamiento t) {
        String sql = "INSERT INTO tratamiento (nombre, descripcion, duracion_min, precio, activo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, t);
            t.setId(Mapeos.insertarYObtenerId(ps));
            return t;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo guardar el tratamiento", e);
        }
    }

    @Override
    public void actualizar(Tratamiento t) {
        String sql = "UPDATE tratamiento SET nombre = ?, descripcion = ?, duracion_min = ?, precio = ?, activo = ? "
                + "WHERE id = ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            asignarCampos(ps, t);
            ps.setInt(6, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo actualizar el tratamiento", e);
        }
    }

    private static void asignarCampos(PreparedStatement ps, Tratamiento t) throws SQLException {
        ps.setString(1, t.getNombre());
        Mapeos.textoOpcional(ps, 2, t.getDescripcion());
        ps.setInt(3, t.getDuracionMinutos());
        ps.setDouble(4, t.getPrecio());
        ps.setInt(5, t.isActivo() ? 1 : 0);
    }

    private List<Tratamiento> consultar(String sql, Integer id) {
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            if (id != null) {
                ps.setInt(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Tratamiento> tratamientos = new ArrayList<>();
                while (rs.next()) {
                    tratamientos.add(Mapeos.tratamiento(rs, ""));
                }
                return tratamientos;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron consultar los tratamientos", e);
        }
    }
}
