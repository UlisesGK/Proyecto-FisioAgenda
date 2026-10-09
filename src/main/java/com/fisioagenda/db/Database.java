package com.fisioagenda.db;

import com.fisioagenda.dao.DataAccessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Punto único de acceso a la base de datos SQLite (singleton).
 * <p>
 * Mantiene una sola conexión JDBC reutilizada durante toda la ejecución, abierta de forma perezosa y
 * con las claves foráneas activadas ({@code PRAGMA foreign_keys = ON}). La ruta del fichero es, por orden
 * de prioridad: la indicada en {@link #configurar(String)}, la propiedad de sistema {@code fisioagenda.db}
 * o {@code ${user.home}/.fisioagenda/fisioagenda.db}.
 */
public final class Database {

    /** Nombre de la propiedad de sistema que permite cambiar la ruta del fichero de base de datos. */
    public static final String PROPIEDAD_RUTA = "fisioagenda.db";

    private static Database instancia;
    private static String rutaConfigurada;

    private final String ruta;
    private Connection conexion;

    private Database(String ruta) {
        this.ruta = ruta;
    }

    /**
     * Fija la ruta del fichero de base de datos y descarta la instancia actual (cerrando su conexión).
     * Pensado para las pruebas, que trabajan con un fichero temporal.
     *
     * @param rutaFichero ruta del fichero SQLite, o {@code null} para volver a la ruta por defecto
     */
    public static synchronized void configurar(String rutaFichero) {
        if (instancia != null) {
            instancia.cerrar();
            instancia = null;
        }
        rutaConfigurada = rutaFichero;
    }

    /** Devuelve la instancia única, creándola con la ruta configurada si aún no existe. */
    public static synchronized Database getInstance() {
        if (instancia == null) {
            instancia = new Database(resolverRuta());
        }
        return instancia;
    }

    private static String resolverRuta() {
        if (rutaConfigurada != null && !rutaConfigurada.isBlank()) {
            return rutaConfigurada;
        }
        String propiedad = System.getProperty(PROPIEDAD_RUTA);
        if (propiedad != null && !propiedad.isBlank()) {
            return propiedad;
        }
        return Paths.get(System.getProperty("user.home"), ".fisioagenda", "fisioagenda.db").toString();
    }

    /** Ruta del fichero de base de datos que usa esta instancia. */
    public String getRuta() {
        return ruta;
    }

    /**
     * Devuelve la conexión compartida, abriéndola si es necesario (crea la carpeta del fichero si no existe).
     * Quien la usa no debe cerrarla: solo debe cerrar sus {@code PreparedStatement} y {@code ResultSet}.
     *
     * @throws DataAccessException si no se puede abrir la base de datos
     */
    public synchronized Connection getConexion() {
        try {
            if (conexion == null || conexion.isClosed()) {
                conexion = abrir();
            }
            return conexion;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo abrir la base de datos en " + ruta, e);
        }
    }

    private Connection abrir() throws SQLException {
        crearCarpetaSiFalta();
        Connection nueva = DriverManager.getConnection("jdbc:sqlite:" + ruta);
        try (Statement st = nueva.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            nueva.close();
            throw e;
        }
        return nueva;
    }

    private void crearCarpetaSiFalta() {
        Path carpeta = Paths.get(ruta).toAbsolutePath().getParent();
        if (carpeta != null && !Files.isDirectory(carpeta)) {
            try {
                Files.createDirectories(carpeta);
            } catch (IOException e) {
                throw new DataAccessException("No se pudo crear la carpeta de datos " + carpeta, e);
            }
        }
    }

    /** Cierra la conexión si está abierta. Una llamada posterior a {@link #getConexion()} la vuelve a abrir. */
    public synchronized void cerrar() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException e) {
                throw new DataAccessException("No se pudo cerrar la base de datos", e);
            } finally {
                conexion = null;
            }
        }
    }
}
