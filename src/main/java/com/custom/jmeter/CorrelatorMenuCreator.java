package com.custom.jmeter;

import org.apache.jmeter.gui.plugin.MenuCreator;
import javax.swing.*;
import java.awt.Frame;

public class CorrelatorMenuCreator implements MenuCreator {
    
    // Store a single instance of our dialog
    private CorrelatorDialog dialogInstance;

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            JMenuItem menuItem = new JMenuItem("UUID Auto-Correlator");
            menuItem.addActionListener(e -> {
                // If it doesn't exist or was closed, create it
                if (dialogInstance == null || !dialogInstance.isDisplayable()) {
                    dialogInstance = new CorrelatorDialog();
                }
                // If it was minimized, restore it to normal
                dialogInstance.setState(Frame.NORMAL);
                dialogInstance.setVisible(true);
                // Bring it to the front of the screen
                dialogInstance.toFront();
            });
            return new JMenuItem[]{menuItem};
        }
        return new JMenuItem[0];
    }

    @Override
    public javax.swing.JMenu[] getTopLevelMenus() { return new javax.swing.JMenu[0]; }
    @Override
    public boolean localeChanged(javax.swing.MenuElement menu) { return false; }
    @Override
    public void localeChanged() {}
}