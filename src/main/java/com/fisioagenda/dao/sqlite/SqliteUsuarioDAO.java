package com.fisioagenda.dao.sqlite;

import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.dao.UsuarioDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.FechasBD;
import com.fisioagenda.model.Rol;
import com.fisioagenda.model.Usuario;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación SQLite de {@link UsuarioDAO}. */
public class SqliteUsuarioDAO implements UsuarioDAO {

    private static final String COLUMNAS = "SELECT id, username, nombre_completo, password_hash, salt, rol, activo, "
            + "intentos_fallidos, bloqueado_hasta FROM usuario";

    private final Database database;

    /** Crea el DAO sobre la base de datos indicada. */
    public SqliteUsuarioDAO(Database database) {
        this.database = database;
    }

    /** Crea el DAO sobre la base de datos por defecto ({@link Database#getInstance()}). */
    public SqliteUsuarioDAO() {
        this(Database.getInstance());
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return buscarUno(COLUMNAS + " WHERE username = ?", username);
    }

    @Override
    public Optional<Usuario> buscarPorId(int id) {
        return buscarUno(COLUMNAS + " WHERE id = ?", id);
    }

    @Override
    public List<Usuario> listarTodos() {
        return listar(COLUMNAS + " ORDER BY nombre_completo COLLATE NOCASE", null);
    }

    @Override
    public List<Usuario> listarPorRol(Rol rol) {
        return listar(COLUMNAS + " WHERE rol = ? ORDER BY nombre_completo COLLATE NOCASE", rol.name());
    }

    @Override
    public Usuario insertar(Usuario u) {
        String sql = "INSERT INTO usuario (username, nombre_completo, password_hash, salt, rol, activo, "
                + "intentos_fallidos, bloqueado_hasta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getNombreCompleto());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getSalt());
            ps.setString(5, u.getRol().name());
            ps.setInt(6, u.isActivo() ? 1 : 0);
            ps.setInt(7, u.getIntentosFallidos());
            Mapeos.textoOpcional(ps, 8, FechasBD.textoPreciso(u.getBloqueadoHasta()));
            u.setId(Mapeos.insertarYObtenerId(ps));
            return u;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo guardar el usuario", e);
        }
    }

    @Override
    public void actualizar(Usuario u) {
        String sql = "UPDATE usuario SET username = ?, nombre_completo = ?, password_hash = ?, salt = ?, rol = ?, "
                + "activo = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getNombreCompleto());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getSalt());
            ps.setString(5, u.getRol().name());
            ps.setInt(6, u.isActivo() ? 1 : 0);
            ps.setInt(7, u.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo actualizar el usuario", e);
        }
    }

    @Override
    public void actualizarControlAcceso(Usuario u) {
        String sql = "UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            ps.setInt(1, u.getIntentosFallidos());
            Mapeos.textoOpcional(ps, 2, FechasBD.textoPreciso(u.getBloqueadoHasta()));
            ps.setInt(3, u.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo actualizar el control de acceso del usuario", e);
        }
    }

    private Optional<Usuario> buscarUno(String sql, Object parametro) {
        List<Usuario> resultado = listar(sql, parametro);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    private List<Usuario> listar(String sql, Object parametro) {
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            if (parametro != null) {
                ps.setObject(1, parametro);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Usuario> usuarios = new ArrayList<>();
                while (rs.next()) {
                    usuarios.add(mapear(rs));
                }
                return usuarios;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron consultar los usuarios", e);
        }
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = Mapeos.usuarioBasico(rs, "");
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setIntentosFallidos(rs.getInt("intentos_fallidos"));
        u.setBloqueadoHasta(FechasBD.fechaHora(rs.getString("bloqueado_hasta")));
        return u;
    }
}
