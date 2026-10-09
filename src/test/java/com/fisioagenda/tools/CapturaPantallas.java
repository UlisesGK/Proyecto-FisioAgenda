package com.fisioagenda.tools;

import com.fisioagenda.Main;
import com.fisioagenda.db.Database;
import com.fisioagenda.db.EsquemaBD;
import com.fisioagenda.model.NotaClinica;
import com.fisioagenda.model.Paciente;
import com.fisioagenda.service.ServiceException;
import com.fisioagenda.service.ServiceLocator;
import com.fisioagenda.ui.LoginFrame;
import com.fisioagenda.ui.MainFrame;
import com.fisioagenda.ui.dialogs.CambiarPasswordDialog;
import com.fisioagenda.ui.dialogs.CitaDialog;
import com.fisioagenda.ui.dialogs.HistorialDialog;
import com.fisioagenda.ui.dialogs.PacienteDialog;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Herramienta de documentación (no es una prueba JUnit, por eso no se ejecuta con {@code mvn test}) que
 * genera las capturas de pantalla de la memoria a partir de las <b>ventanas reales</b> de la aplicación.
 * <p>
 * Arranca FisioAgenda mediante {@link Main#main(String[])} sobre una base de datos de demostración nueva
 * en un fichero temporal, recorre las pantallas como lo haría un usuario (pulsando los mismos botones) y
 * guarda cada ventana como PNG pintando su contenido en una imagen. Los diálogos modales se abren con
 * {@code invokeLater}, de modo que el hilo de la herramienta nunca queda bloqueado. Las capturas se
 * escriben en {@code docs/capturas} (o en la carpeta indicada como primer argumento).
 * <p>
 * La imagen usa la escala de la pantalla. Con {@code -Dsun.java2d.uiScale=2} Java mide y pinta el texto a
 * doble resolución, y se obtienen capturas nítidas (2560x1600 para la ventana principal de 1280x800) sin
 * textos recortados. Uso desde la raíz del proyecto (en Linux sin escritorio, dentro de {@code xvfb-run}):
 * <pre>
 * mvn -o -q test-compile
 * M2=~/.m2/repository
 * CP=target/classes:target/test-classes:$M2/com/formdev/flatlaf/3.7.2/flatlaf-3.7.2.jar:$M2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar
 * xvfb-run -a -s '-screen 0 2880x1800x24' java -Dsun.java2d.uiScale=2 -cp "$CP" com.fisioagenda.tools.CapturaPantallas
 * </pre>
 */
public final class CapturaPantallas {

    private static final int ANCHO_VENTANA = 1280;
    private static final int ALTO_VENTANA = 800;
    private static final long ESPERA_MAXIMA_MS = 15_000;
    /** Margen para que terminen las animaciones (la más larga, el gráfico de barras, dura 600 ms). */
    private static final long PAUSA_ANIMACIONES_MS = 1_000;
    private static final long TIEMPO_MAXIMO_TOTAL_MS = 240_000;
    private static final Color BORDE_VENTANA = new Color(0xCBD5E1);

    private final Path carpetaSalida;
    private final List<String> generadas = new ArrayList<>();

    private CapturaPantallas(Path carpetaSalida) {
        this.carpetaSalida = carpetaSalida;
    }

    /**
     * Genera todas las capturas.
     *
     * @param args carpeta de salida opcional (por defecto {@code docs/capturas})
     */
    public static void main(String[] args) throws Exception {
        // Suavizado de texto en escala de grises: sin escritorio (Xvfb) no hay preferencias del sistema.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        Path salida = Paths.get(args.length > 0 ? args[0] : "docs/capturas").toAbsolutePath();
        Files.createDirectories(salida);

        Path carpetaTemporal = Files.createTempDirectory("fisioagenda-capturas");
        Path bd = carpetaTemporal.resolve("demo.db");
        System.setProperty(Database.PROPIEDAD_RUTA, bd.toString());
        iniciarVigilante();

        int codigo = 0;
        try {
            new CapturaPantallas(salida).ejecutar();
        } catch (Exception | AssertionError e) {
            e.printStackTrace();
            codigo = 1;
        } finally {
            try {
                Database.getInstance().cerrar();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            borrarCarpeta(carpetaTemporal);
        }
        System.exit(codigo);
    }

    /** Recorre la aplicación y guarda las capturas en orden. */
    private void ejecutar() throws Exception {
        Main.main(new String[0]);

        // ---------------------------------------------------------------- acceso
        LoginFrame login = esperarVentana(LoginFrame.class);
        pausa(PAUSA_ANIMACIONES_MS); // fundido de entrada
        capturar(login, "01_login.png");

        enEdt(() -> {
            escribir(campo(login, "Usuario"), "admin");
            escribir(campo(login, "Contraseña"), "Clave-incorrecta1");
            pulsar(boton(login, "Entrar"));
        });
        esperar("respuesta del login fallido", () -> botonOpcional(login, "Entrar")
                .map(AbstractButton::isEnabled).orElse(false));
        pausa(PAUSA_ANIMACIONES_MS); // sacudida del formulario
        capturar(login, "02_login_error.png");

        MainFrame admin = iniciarSesion(login, "admin", "Admin1234!");
        capturar(admin, "03_inicio_admin.png");

        // ---------------------------------------------------------------- pacientes
        irASeccion(admin, "Pacientes");
        seleccionarFila(admin, 2);
        capturar(admin, "04_pacientes.png");

        PacienteDialog dlgPaciente = abrirDialogo(PacienteDialog.class, () -> pulsar(boton(admin, "Nuevo")));
        enEdt(() -> {
            escribir(campo(dlgPaciente, "DNI / NIE *"), "12345678A"); // letra incorrecta (sería Z)
            escribir(campo(dlgPaciente, "Teléfono *"), "612 345 678");
            escribir(campo(dlgPaciente, "Nombre *"), "Pablo");
            escribir(campo(dlgPaciente, "Apellidos *"), "Ruiz Ortega");
            escribir(campo(dlgPaciente, "Email"), "pablo.ruiz@example");
            escribir(campo(dlgPaciente, "Fecha de nacimiento"), "14/03/1988");
            escribir(campo(dlgPaciente, "Observaciones"), "Lumbalgia mecánica de tres semanas de evolución.");
            pulsar(boton(dlgPaciente, "Guardar"));
        });
        pausa(500);
        capturar(dlgPaciente, "05_paciente_dialogo.png");
        cerrar(dlgPaciente);

        // ---------------------------------------------------------------- agenda
        irASeccion(admin, "Agenda");
        LocalDate hoy = LocalDate.now();
        if (!EsquemaBD.diaDeReferencia(hoy).equals(hoy)) {
            // Domingo: la demostración coloca las citas "de hoy" el lunes.
            enEdt(() -> pulsar(botonPorAyuda(admin, "Día siguiente")));
            esperarFilas(admin);
        }
        enEdt(() -> seleccionarPrimeraProgramada(admin));
        pausa(400);
        capturar(admin, "06_agenda.png");

        CitaDialog dlgCita = abrirDialogo(CitaDialog.class, () -> pulsar(boton(admin, "Nueva cita")));
        LocalDate diaCita = siguienteDiaLaborable(LocalDate.now());
        enEdt(() -> {
            escribir(campo(dlgCita, "Paciente *"), "Lucía");
            buscar(dlgCita, JSpinner.class, s -> true).get(0)
                    .setValue(Date.from(diaCita.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            escribir(campo(dlgCita, "Notas"), "Revisión del plan de ejercicios domiciliarios.");
        });
        enEdt(() -> {
            @SuppressWarnings("unchecked")
            JComboBox<Object> huecos = (JComboBox<Object>) campo(dlgCita, "Hora (huecos libres) *");
            for (int i = 0; i < huecos.getItemCount(); i++) {
                if (LocalTime.of(10, 0).equals(huecos.getItemAt(i))) {
                    huecos.setSelectedIndex(i);
                }
            }
        });
        pausa(500);
        capturar(dlgCita, "07_cita_dialogo.png");
        cerrar(dlgCita);

        // ---------------------------------------------------------------- tratamientos y usuarios
        irASeccion(admin, "Tratamientos");
        seleccionarFila(admin, 0);
        capturar(admin, "09_tratamientos.png");

        // Cinco intentos fallidos bloquean la cuenta de fisio2 (se ve en la columna "Bloqueado").
        for (int i = 0; i < 5; i++) {
            try {
                ServiceLocator.auth().login("fisio2", "Incorrecta-1".toCharArray());
            } catch (ServiceException esperado) {
                // Es justo lo que se busca: un intento fallido más.
            }
        }
        irASeccion(admin, "Usuarios");
        enEdt(() -> seleccionarFilaConValor(tablaVisible(admin), 0, "fisio2"));
        pausa(400);
        capturar(admin, "10_usuarios.png");

        CambiarPasswordDialog dlgPassword = abrirDialogo(CambiarPasswordDialog.class,
                () -> pulsar(boton(admin, "Cambiar contraseña")));
        enEdt(() -> {
            escribir(campo(dlgPassword, "Contraseña actual"), "Admin1234!");
            escribir(campo(dlgPassword, "Nueva contraseña"), "Clinica#2026");
            escribir(campo(dlgPassword, "Repetir nueva contraseña"), "Clinica#2026");
        });
        pausa(PAUSA_ANIMACIONES_MS); // barra de fortaleza animada
        capturar(dlgPassword, "13_cambiar_password.png");
        cerrar(dlgPassword);

        // ---------------------------------------------------------------- diseño adaptable
        irASeccion(admin, "Inicio");
        enEdt(() -> {
            admin.setSize(1000, 680);
            admin.setLocationRelativeTo(null);
            admin.validate();
        });
        pausa(PAUSA_ANIMACIONES_MS); // la barra lateral se pliega con animación
        enEdt(admin::validate);
        capturar(admin, "12_responsive_colapsado.png");

        // ---------------------------------------------------------------- sesión de fisioterapeuta
        LoginFrame login2 = cerrarSesion(admin);
        MainFrame fisio = iniciarSesion(login2, "fisio", "Fisio1234!");
        capturar(fisio, "11_inicio_fisio.png");

        Paciente conMasNotas = pacienteConMasNotas();
        irASeccion(fisio, "Pacientes");
        enEdt(() -> seleccionarFilaConValor(tablaVisible(fisio), 0, conMasNotas.getDni()));
        HistorialDialog dlgHistorial = abrirDialogo(HistorialDialog.class, () -> pulsar(boton(fisio, "Historial")));
        pausa(PAUSA_ANIMACIONES_MS);
        capturar(dlgHistorial, "08_historial.png");
        cerrar(dlgHistorial);

        // ---------------------------------------------------------------- sesión de recepción
        LoginFrame login3 = cerrarSesion(fisio);
        MainFrame recepcion = iniciarSesion(login3, "recepcion", "Recep1234!");
        irASeccion(recepcion, "Pacientes");
        seleccionarFila(recepcion, 0);
        capturar(recepcion, "14_recepcion_sin_historial.png");

        System.out.println("Capturas generadas en " + carpetaSalida + ": " + String.join(", ", generadas));
        enEdt(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    // ==================================================================== pasos de la aplicación

    /** Escribe las credenciales, pulsa "Entrar" y espera a la ventana principal (ya cargada, a 1280x800). */
    private MainFrame iniciarSesion(LoginFrame login, String usuario, String password) throws Exception {
        enEdt(() -> {
            escribir(campo(login, "Usuario"), usuario);
            escribir(campo(login, "Contraseña"), password);
            pulsar(boton(login, "Entrar"));
        });
        MainFrame principal = esperarVentana(MainFrame.class);
        enEdt(() -> {
            principal.setSize(ANCHO_VENTANA, ALTO_VENTANA);
            principal.setLocationRelativeTo(null);
            principal.validate();
        });
        esperarFilas(principal);
        pausa(PAUSA_ANIMACIONES_MS);
        return principal;
    }

    /** Cierra la sesión desde la barra lateral confirmando con "Sí" y devuelve la nueva ventana de acceso. */
    private static LoginFrame cerrarSesion(MainFrame principal) throws Exception {
        SwingUtilities.invokeLater(() -> pulsar(boton(principal, "Cerrar sesión")));
        JDialog confirmacion = esperarVentana(JDialog.class,
                d -> !buscar(d, JOptionPane.class, o -> true).isEmpty());
        enEdt(() -> pulsar(boton(confirmacion, "Sí")));
        esperar("cierre de la ventana principal", () -> !principal.isDisplayable());
        LoginFrame login = esperarVentana(LoginFrame.class);
        pausa(PAUSA_ANIMACIONES_MS);
        return login;
    }

    /** Pulsa el botón de la barra lateral y espera a que la sección cargue sus datos. */
    private static void irASeccion(MainFrame principal, String seccion) throws Exception {
        enEdt(() -> pulsar(boton(principal, seccion)));
        esperarFilas(principal);
        pausa(PAUSA_ANIMACIONES_MS);
    }

    /** Lanza una acción que abre un diálogo modal (sin bloquear este hilo) y espera a que se muestre. */
    private static <D extends JDialog> D abrirDialogo(Class<D> tipo, Runnable accion) throws Exception {
        SwingUtilities.invokeLater(accion);
        D dialogo = esperarVentana(tipo);
        pausa(600);
        return dialogo;
    }

    private static void cerrar(Window ventana) throws Exception {
        enEdt(ventana::dispose);
        esperar("cierre de " + ventana.getClass().getSimpleName(), () -> !ventana.isDisplayable());
        pausa(300);
    }

    /** Paciente con más notas clínicas (requiere una sesión con acceso al historial). */
    private static Paciente pacienteConMasNotas() {
        Paciente elegido = null;
        int maximo = -1;
        for (Paciente p : ServiceLocator.pacientes().listar()) {
            List<NotaClinica> notas = ServiceLocator.historial().notasDe(p.getId());
            if (notas.size() > maximo) {
                maximo = notas.size();
                elegido = p;
            }
        }
        return Objects.requireNonNull(elegido, "No hay pacientes en la demostración");
    }

    private static LocalDate siguienteDiaLaborable(LocalDate desde) {
        LocalDate dia = desde.plusDays(1);
        return dia.getDayOfWeek() == DayOfWeek.SUNDAY ? dia.plusDays(1) : dia;
    }

    private static void seleccionarFila(Window ventana, int fila) throws Exception {
        enEdt(() -> {
            JTable tabla = tablaVisible(ventana);
            int f = Math.min(fila, tabla.getRowCount() - 1);
            if (f >= 0) {
                tabla.setRowSelectionInterval(f, f);
            }
        });
        pausa(400);
    }

    private static void seleccionarFilaConValor(JTable tabla, int columna, Object valor) {
        for (int i = 0; i < tabla.getRowCount(); i++) {
            if (Objects.equals(valor, tabla.getValueAt(i, columna))) {
                tabla.setRowSelectionInterval(i, i);
                tabla.scrollRectToVisible(tabla.getCellRect(i, 0, true));
                return;
            }
        }
        throw new IllegalStateException("No se encontró la fila con el valor " + valor);
    }

    /** Selecciona en la agenda la primera cita programada (habilita los botones de acción). */
    private static void seleccionarPrimeraProgramada(Window ventana) {
        JTable tabla = tablaVisible(ventana);
        for (int i = 0; i < tabla.getRowCount(); i++) {
            for (int c = 0; c < tabla.getColumnCount(); c++) {
                if ("Programada".equals(String.valueOf(tabla.getValueAt(i, c)))) {
                    tabla.setRowSelectionInterval(i, i);
                    return;
                }
            }
        }
    }

    /**
     * Espera a que la tabla visible tenga datos. No falla si sigue vacía (por ejemplo, el panel de inicio
     * un domingo, cuando la clínica no tiene citas): en ese caso continúa tras un tiempo prudencial.
     */
    private static void esperarFilas(Window ventana) throws Exception {
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            boolean conDatos = enEdt(() -> {
                List<JTable> tablas = buscar(ventana, JTable.class, Component::isShowing);
                return !tablas.isEmpty() && tablas.get(0).getRowCount() > 0;
            });
            if (conDatos) {
                return;
            }
            pausa(100);
        }
        System.out.println("  (aviso: la tabla visible sigue vacía; se continúa)");
    }

    // ==================================================================== captura

    /** Pinta el contenido de la ventana en una imagen (a la escala de la pantalla) y la guarda como PNG. */
    private void capturar(Window ventana, String nombre) throws Exception {
        BufferedImage imagen = enEdt(() -> {
            ventana.validate();
            JComponent raiz = ((RootPaneContainer) ventana).getRootPane();
            // Misma escala que la pantalla (-Dsun.java2d.uiScale): el texto se pinta con las medidas del diseño.
            double escala = ventana.getGraphicsConfiguration().getDefaultTransform().getScaleX();
            int ancho = (int) Math.round(raiz.getWidth() * escala);
            int alto = (int) Math.round(raiz.getHeight() * escala);
            BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, ancho, alto);
                g.scale(escala, escala);
                raiz.paint(g);
                // Contorno fino para delimitar la ventana dentro del documento.
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setColor(BORDE_VENTANA);
                g.drawRect(0, 0, raiz.getWidth() - 1, raiz.getHeight() - 1);
            } finally {
                g.dispose();
            }
            return img;
        });
        Path destino = carpetaSalida.resolve(nombre);
        ImageIO.write(imagen, "png", destino.toFile());
        generadas.add(nombre);
        System.out.println("  " + nombre + " (" + imagen.getWidth() + "x" + imagen.getHeight() + ")");
    }

    // ==================================================================== búsqueda de componentes

    /** Componentes de un tipo dentro de un contenedor (recorrido en profundidad). */
    private static <C extends Component> List<C> buscar(Container raiz, Class<C> tipo, Predicate<C> filtro) {
        List<C> encontrados = new ArrayList<>();
        for (Component c : raiz.getComponents()) {
            if (tipo.isInstance(c) && filtro.test(tipo.cast(c))) {
                encontrados.add(tipo.cast(c));
            }
            if (c instanceof Container) {
                encontrados.addAll(buscar((Container) c, tipo, filtro));
            }
        }
        return encontrados;
    }

    private static Optional<AbstractButton> botonOpcional(Container raiz, String texto) {
        return buscar(raiz, AbstractButton.class, b -> b.isShowing() && texto.equals(b.getText()))
                .stream().findFirst();
    }

    /** Botón visible con el texto indicado. */
    private static AbstractButton boton(Container raiz, String texto) {
        return botonOpcional(raiz, texto)
                .orElseThrow(() -> new IllegalStateException("No se encontró el botón «" + texto + "»"));
    }

    /** Botón visible cuya ayuda emergente empieza por el texto indicado (botones que solo tienen icono). */
    private static AbstractButton botonPorAyuda(Container raiz, String ayuda) {
        return buscar(raiz, AbstractButton.class, b -> b.isShowing() && b.getToolTipText() != null
                && b.getToolTipText().startsWith(ayuda)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró el botón «" + ayuda + "»"));
    }

    /** Campo asociado (con {@code setLabelFor}) a la etiqueta visible con el texto indicado. */
    private static JComponent campo(Container raiz, String etiqueta) {
        return buscar(raiz, JLabel.class, l -> l.isShowing() && etiqueta.equals(l.getText()) && l.getLabelFor() != null)
                .stream().findFirst().map(l -> (JComponent) l.getLabelFor())
                .orElseThrow(() -> new IllegalStateException("No se encontró el campo «" + etiqueta + "»"));
    }

    /** Pulsa un botón como lo haría el ratón: primero recibe el foco y después se ejecuta su acción. */
    private static void pulsar(AbstractButton boton) {
        boton.requestFocusInWindow();
        boton.doClick();
    }

    private static void escribir(JComponent campo, String texto) {
        ((JTextComponent) campo).setText(texto);
    }

    private static JTable tablaVisible(Window ventana) {
        return buscar(ventana, JTable.class, Component::isShowing).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay ninguna tabla visible"));
    }

    // ==================================================================== sincronización

    private static <W extends Window> W esperarVentana(Class<W> tipo) throws Exception {
        return esperarVentana(tipo, w -> true);
    }

    /** Espera a que se muestre una ventana del tipo indicado y la devuelve (la más reciente). */
    private static <W extends Window> W esperarVentana(Class<W> tipo, Predicate<W> filtro) throws Exception {
        List<W> encontrada = new ArrayList<>();
        esperar("ventana " + tipo.getSimpleName(), () -> {
            encontrada.clear();
            for (Window w : Window.getWindows()) {
                if (tipo.isInstance(w) && w.isShowing() && filtro.test(tipo.cast(w))) {
                    encontrada.add(tipo.cast(w));
                }
            }
            return !encontrada.isEmpty();
        });
        return encontrada.get(encontrada.size() - 1);
    }

    /** Comprueba la condición en el EDT cada 100 ms hasta que se cumpla o se agote el tiempo. */
    private static void esperar(String descripcion, BooleanSupplier condicion) throws Exception {
        long limite = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        while (!enEdt(condicion::getAsBoolean)) {
            if (System.currentTimeMillis() > limite) {
                throw new IllegalStateException("Tiempo agotado esperando: " + descripcion);
            }
            pausa(100);
        }
    }

    private static void enEdt(Runnable accion) throws Exception {
        enEdt(() -> {
            accion.run();
            return null;
        });
    }

    /** Ejecuta en el hilo de eventos de Swing y devuelve el resultado (propaga las excepciones). */
    private static <T> T enEdt(Callable<T> accion) throws Exception {
        List<T> resultado = new ArrayList<>(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    resultado.add(accion.call());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (InvocationTargetException e) {
            Throwable causa = e.getCause();
            throw causa instanceof Exception ? (Exception) causa : new IllegalStateException(causa);
        }
        return resultado.get(0);
    }

    private static void pausa(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    /** Termina el proceso si algo se queda bloqueado (por ejemplo, un diálogo inesperado). */
    private static void iniciarVigilante() {
        Thread vigilante = new Thread(() -> {
            try {
                Thread.sleep(TIEMPO_MAXIMO_TOTAL_MS);
                System.err.println("Las capturas tardan demasiado: se cancela el proceso.");
                Runtime.getRuntime().halt(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "vigilante-capturas");
        vigilante.setDaemon(true);
        vigilante.start();
    }

    private static void borrarCarpeta(Path carpeta) {
        try (var ficheros = Files.walk(carpeta)) {
            ficheros.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    p.toFile().deleteOnExit();
                }
            });
        } catch (IOException e) {
            carpeta.toFile().deleteOnExit();
        }
    }
}
