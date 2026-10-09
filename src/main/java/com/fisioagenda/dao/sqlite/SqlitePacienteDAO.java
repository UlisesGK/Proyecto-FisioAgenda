package com.fisioagenda.dao.sqlite;

import com.fisioagenda.dao.DataAccessException;
import com.fisioagenda.dao.PacienteDAO;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.FechasBD;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.util.Textos;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Implementación SQLite de {@link PacienteDAO}. */
public class SqlitePacienteDAO implements PacienteDAO {

    private static final String COLUMNAS = "SELECT id, dni, nombre, apellidos, telefono, email, fecha_nacimiento, "
            + "observaciones, fecha_alta FROM paciente";
    private static final String ORDEN = " ORDER BY apellidos COLLATE NOCASE, nombre COLLATE NOCASE";

    private final Database database;

    /** Crea el DAO sobre la base de datos indicada. */
    public SqlitePacienteDAO(Database database) {
        this.database = database;
    }

    /** Crea el DAO sobre la base de datos por defecto ({@link Database#getInstance()}). */
    public SqlitePacienteDAO() {
        this(Database.getInstance());
    }

    @Override
    public Optional<Paciente> buscarPorId(int id) {
        List<Paciente> resultado = consultar(COLUMNAS + " WHERE id = ?", id);
        return resultado.stream().findFirst();
    }

    @Override
    public Optional<Paciente> buscarPorDni(String dni) {
        List<Paciente> resultado = consultar(COLUMNAS + " WHERE dni = ?", dni);
        return resultado.stream().findFirst();
    }

    @Override
    public List<Paciente> listarTodos() {
        return consultar(COLUMNAS + ORDEN);
    }

    /**
     * {@inheritDoc}
     * <p>Cada palabra del texto debe aparecer en el nombre, los apellidos, el DNI o el teléfono, sin distinguir
     * mayúsculas ni tildes ({@link Textos#contienePalabras}). El filtrado se hace en Java porque el {@code LIKE}
     * de SQLite solo ignora las mayúsculas en letras ASCII y no ignora tildes ("alvaro" no encontraría a
     * "Álvaro"). Para el volumen de pacientes de una clínica pequeña recorrer la lista es inmediato.
     */
    @Override
    public List<Paciente> buscar(String texto) {
        List<Paciente> todos = listarTodos();
        if (texto == null || texto.isBlank()) {
            return todos;
        }
        List<Paciente> encontrados = new ArrayList<>();
        for (Paciente p : todos) {
            String datos = p.getNombre() + " " + p.getApellidos() + " " + p.getDni() + " " + p.getTelefono();
            if (Textos.contienePalabras(datos, texto)) {
                encontrados.add(p);
            }
        }
        return encontrados;
    }

    @Override
    public Paciente insertar(Paciente p) {
        String sql = "INSERT INTO paciente (dni, nombre, apellidos, telefono, email, fecha_nacimiento, observaciones, "
                + "fecha_alta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, p);
            ps.setString(8, FechasBD.texto(p.getFechaAlta()));
            p.setId(Mapeos.insertarYObtenerId(ps));
            return p;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo guardar el paciente", e);
        }
    }

    @Override
    public void actualizar(Paciente p) {
        String sql = "UPDATE paciente SET dni = ?, nombre = ?, apellidos = ?, telefono = ?, email = ?, "
                + "fecha_nacimiento = ?, observaciones = ?, fecha_alta = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            asignarCampos(ps, p);
            ps.setString(8, FechasBD.texto(p.getFechaAlta()));
            ps.setInt(9, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo actualizar el paciente", e);
        }
    }

    /** {@inheritDoc} Sus citas y notas clínicas se eliminan en cascada. */
    @Override
    public void eliminar(int id) {
        try (PreparedStatement ps = database.getConexion().prepareStatement("DELETE FROM paciente WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo eliminar el paciente", e);
        }
    }

    @Override
    public int contar() {
        try (PreparedStatement ps = database.getConexion().prepareStatement("SELECT COUNT(*) FROM paciente");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron contar los pacientes", e);
        }
    }

    private static void asignarCampos(PreparedStatement ps, Paciente p) throws SQLException {
        ps.setString(1, p.getDni());
        ps.setString(2, p.getNombre());
        ps.setString(3, p.getApellidos());
        ps.setString(4, p.getTelefono());
        Mapeos.textoOpcional(ps, 5, p.getEmail());
        Mapeos.textoOpcional(ps, 6, FechasBD.texto(p.getFechaNacimiento()));
        Mapeos.textoOpcional(ps, 7, p.getObservaciones());
    }

    private List<Paciente> consultar(String sql, Object... parametros) {
        try (PreparedStatement ps = database.getConexion().prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Paciente> pacientes = new ArrayList<>();
                while (rs.next()) {
                    pacientes.add(Mapeos.paciente(rs, ""));
                }
                return pacientes;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudieron consultar los pacientes", e);
        }
    }
}
