package com.fisioagenda;

import com.fisioagenda.db.Database;
import com.fisioagenda.db.EsquemaBD;
import com.fisioagenda.ui.LoginFrame;
import com.fisioagenda.ui.Tema;
import com.fisioagenda.util.Formatos;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.Collections;
import java.util.Locale;

/**
 * Punto de entrada de FisioAgenda: configura el aspecto (FlatLaf con el color de acento de la
 * clínica), inicializa la base de datos SQLite y abre la ventana de inicio de sesión en el EDT.
 */
public final class Main {

    private Main() {
    }

    /**
     * Arranca la aplicación.
     *
     * @param args no se usan
     */
    public static void main(String[] args) {
        // En Windows, el pipeline Direct3D de Java2D pinta la ventana descolocada o a medias con algunas
        // gráficas y escalados de pantalla (125 %, 150 %). Se desactiva antes de crear cualquier ventana.
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        // Textos estándar de Swing (Aceptar, Cancelar...) y formatos en español, sea cual sea el idioma del sistema.
        Locale.setDefault(Formatos.LOCALE);
        configurarAspecto();
        try {
            Database.getInstance();
            EsquemaBD.inicializar();
        } catch (RuntimeException e) {
            System.err.println("No se pudo inicializar la base de datos: " + e.getMessage());
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "No se pudo abrir la base de datos de FisioAgenda.\n\nDetalle: " + e.getMessage(),
                        "Error al iniciar FisioAgenda", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
            return;
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().mostrar());
    }

    /** Instala FlatLaf y ajusta redondeos, foco, tipografía y colores comunes. */
    private static void configurarAspecto() {
        FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", Tema.hex(Tema.PRIMARIO)));
        FlatLightLaf.setup();
        UIManager.put("defaultFont", new FontUIResource(Tema.FAMILIA, Font.PLAIN, 13));
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        // Las áreas de texto van dentro de un JScrollPane: mismo redondeo que el resto de campos del formulario.
        UIManager.put("ScrollPane.TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.arrowType", "chevron");
        // El botón por defecto (Intro) de cada ventana es siempre la acción principal: relleno corporativo.
        UIManager.put("Button.default.background", Tema.PRIMARIO);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.focusedBackground", Tema.PRIMARIO);
        UIManager.put("Button.default.hoverBackground", Tema.PRIMARIO_OSCURO);
        UIManager.put("Button.default.pressedBackground", new Color(0x0B4F4B));
        UIManager.put("Button.default.borderColor", Tema.PRIMARIO);
        UIManager.put("Button.default.hoverBorderColor", Tema.PRIMARIO_OSCURO);
        UIManager.put("Button.default.focusedBorderColor", new Color(0x5EEAD4));
        UIManager.put("Button.default.focusColor", new Color(0x5EEAD4));
        UIManager.put("Button.default.borderWidth", 1);
        UIManager.put("Button.default.boldText", true);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Table.alternateRowColor", new Color(0xF7FAFA));
        UIManager.put("Table.selectionBackground", Tema.suavizar(Tema.PRIMARIO, 0.8));
        UIManager.put("Table.selectionForeground", Tema.TEXTO);
        UIManager.put("Table.selectionInactiveBackground", Tema.suavizar(Tema.PRIMARIO, 0.88));
        UIManager.put("Table.selectionInactiveForeground", Tema.TEXTO);
        UIManager.put("Table.cellMargins", new Insets(2, 12, 2, 12));
        UIManager.put("TableHeader.background", Tema.SUPERFICIE);
        UIManager.put("TableHeader.separatorColor", Tema.SUPERFICIE);
        UIManager.put("TableHeader.bottomSeparatorColor", Tema.BORDE);
        UIManager.put("TableHeader.cellMargins", new Insets(4, 12, 4, 12));
        UIManager.put("List.selectionBackground", Tema.suavizar(Tema.PRIMARIO, 0.8));
        UIManager.put("List.selectionForeground", Tema.TEXTO);
        UIManager.put("List.selectionInactiveBackground", Tema.suavizar(Tema.PRIMARIO, 0.88));
        UIManager.put("List.selectionInactiveForeground", Tema.TEXTO);
        UIManager.put("ToolTip.background", new Color(0x1E293B));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 10, 6, 10));
        UIManager.put("PopupMenu.borderCornerRadius", 10);
    }
}
