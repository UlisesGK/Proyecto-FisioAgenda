package com.fisioagenda.ui.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JRootPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del selector numérico de los formularios (precio y duración de los tratamientos). No abren ventanas,
 * así que funcionan también sin pantalla.
 */
class SelectorNumericoTest {

    private static JSpinner precio() {
        return Componentes.selectorNumerico(new SpinnerNumberModel(35.0, 0.0, 9999.0, 0.5), "0.00");
    }

    private static JSpinner duracion() {
        return Componentes.selectorNumerico(new SpinnerNumberModel(45, 10, 240, 5), "0");
    }

    private static JFormattedTextField campo(JSpinner selector) {
        return ((JSpinner.DefaultEditor) selector.getEditor()).getTextField();
    }

    @ParameterizedTest(name = "precio \"{0}\" = {1}")
    @CsvSource(delimiter = ';', value = {"35,5; 35.5", "35.5; 35.5", "12; 12", " 7,25 ; 7.25", "0; 0", "9999; 9999"})
    void elPrecioAdmiteComaOPuntoDecimal(String texto, double esperado) throws ParseException {
        JSpinner selector = precio();
        campo(selector).setText(texto);
        campo(selector).commitEdit();
        assertEquals(esperado, ((Number) selector.getValue()).doubleValue(), 1e-9);
    }

    @ParameterizedTest(name = "precio \"{0}\" se rechaza")
    @ValueSource(strings = {"12abc", "1.234,5", "1,2,3", "35,555", "1.234", "-5", "10000", "", "abc"})
    void elPrecioRechazaTextosQueAntesSeTruncabanEnSilencio(String texto) {
        JSpinner selector = precio();
        campo(selector).setText(texto);
        assertThrows(ParseException.class, () -> campo(selector).commitEdit());
        assertEquals(35.0, selector.getValue(), "El valor no cambia");
    }

    @ParameterizedTest(name = "duración \"{0}\" se rechaza")
    @ValueSource(strings = {"12,5", "12.5", "60min", "5", "241"})
    void laDuracionSoloAdmiteEnterosDentroDeLosLimites(String texto) {
        JSpinner selector = duracion();
        campo(selector).setText(texto);
        assertThrows(ParseException.class, () -> campo(selector).commitEdit());
        assertEquals(45, selector.getValue());
    }

    @Test
    void laDuracionAdmiteEnterosValidos() throws ParseException {
        JSpinner selector = duracion();
        campo(selector).setText("60");
        campo(selector).commitEdit();
        assertEquals(60, selector.getValue());
    }

    @Test
    void unTextoNoValidoSeConservaAlSalirDelCampo() {
        assertEquals(JFormattedTextField.COMMIT, campo(precio()).getFocusLostBehavior());
    }

    @Test
    void elIntroQueConfirmaElValorPulsaTambienElBotonPorDefecto() throws ParseException {
        JSpinner selector = duracion();
        JRootPane raiz = new JRootPane();
        raiz.getContentPane().add(selector);
        AtomicInteger pulsaciones = new AtomicInteger();
        JButton guardar = new JButton("Guardar");
        guardar.addActionListener(e -> pulsaciones.incrementAndGet());
        raiz.getContentPane().add(guardar);
        raiz.setDefaultButton(guardar);

        // Con el campo editado, Intro ejecuta la acción de confirmar del campo, que consume la tecla: primero
        // confirma el valor y después avisa a los ActionListener del campo (postActionEvent).
        JFormattedTextField campo = campo(selector);
        campo.setText("60");
        campo.commitEdit();
        campo.postActionEvent();
        assertEquals(60, selector.getValue());
        assertEquals(1, pulsaciones.get(), "Un solo Intro basta para guardar");

        // Si el botón por defecto está deshabilitado (p. ej. guardando), no se pulsa
        guardar.setEnabled(false);
        campo.postActionEvent();
        assertEquals(1, pulsaciones.get());
    }
}
