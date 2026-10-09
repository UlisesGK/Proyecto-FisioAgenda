package com.fisioagenda.ui.components;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;

/**
 * Base de los componentes pintados a mano con Java2D (gráficos, avatares, notificaciones...).
 * {@link JComponent} no crea contexto de accesibilidad por defecto; esta clase lo proporciona para
 * que los lectores de pantalla puedan anunciar su nombre y descripción.
 */
public abstract class ComponenteDibujado extends JComponent {

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.LABEL;
                }
            };
        }
        return accessibleContext;
    }
}
