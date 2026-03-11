package com.custom.jmeter;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.MultiProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.testelement.property.TestElementProperty;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CorrelatorDialog extends JFrame {
    private JTable table;
    private DefaultTableModel tableModel;
    
    private final Pattern boundaryPattern = Pattern.compile("EntityID%22%3A%22(.*?)%22%2C%22IsDirty");
    private boolean isSelectAll = true;
    private int variableCounter = 1;

    public CorrelatorDialog() {
        setTitle("UUID Auto-Correlator");
        setSize(700, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 

        // Setup Table
        String[] columns = {"Select", "Values Found", "Variable Name"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class; 
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 2; 
            }
        };
        table = new JTable(tableModel);
        
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(400);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Setup Buttons
        JPanel buttonPanel = new JPanel();
        
        JButton scanBtn = new JButton("Scan Script");
        JButton selectAllBtn = new JButton("Select All");
        JButton correlateBtn = new JButton("Correlate");
        JButton clearBtn = new JButton("Clear");

        correlateBtn.setForeground(Color.BLACK);
        correlateBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        buttonPanel.add(scanBtn);
        buttonPanel.add(selectAllBtn);
        buttonPanel.add(correlateBtn);
        buttonPanel.add(clearBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Button Actions
        scanBtn.addActionListener(e -> scanTestPlan());
        selectAllBtn.addActionListener(e -> toggleSelectAll());
        clearBtn.addActionListener(e -> tableModel.setRowCount(0));
        correlateBtn.addActionListener(e -> correlateValues());

        setupAutoMinimizeListener();
    }

    private void setupAutoMinimizeListener() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage != null && guiPackage.getTreeListener() != null) {
            JTree jMeterTree = guiPackage.getTreeListener().getJTree();
            jMeterTree.addTreeSelectionListener(e -> {
                if (CorrelatorDialog.this.isVisible() && CorrelatorDialog.this.getState() == Frame.NORMAL) {
                    CorrelatorDialog.this.setState(Frame.ICONIFIED);
                }
            });
        }
    }

    private void toggleSelectAll() {
        isSelectAll = !isSelectAll;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(isSelectAll, i, 0);
        }
    }

    private void scanTestPlan() {
        tableModel.setRowCount(0);
        variableCounter = 1; 
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) return;

        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        traverseAndFind(treeModel.getRoot());
    }

    private void traverseAndFind(Object node) {
        JMeterTreeNode treeNode = (JMeterTreeNode) node;
        TestElement element = treeNode.getTestElement();

        PropertyIterator iter = element.propertyIterator();
        while (iter.hasNext()) {
            JMeterProperty prop = iter.next();
            String value = prop.getStringValue();
            if (value != null) {
                Matcher m = boundaryPattern.matcher(value);
                while (m.find()) {
                    String foundValue = m.group(1).trim();
                    if (!isAlreadyInTable(foundValue)) {
                        tableModel.addRow(new Object[]{true, foundValue, "C_EntityID" + variableCounter});
                        variableCounter++;
                    }
                }
            }
        }

        for (int i = 0; i < treeNode.getChildCount(); i++) {
            traverseAndFind(treeNode.getChildAt(i));
        }
    }

    private boolean isAlreadyInTable(String val) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 1).equals(val)) return true;
        }
        return false;
    }

    private void correlateValues() {
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage == null) return;

        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No values to correlate. Please scan the script first.");
            return;
        }

        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        boolean anySelected = false;
        
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
            if (isSelected != null && isSelected) {
                anySelected = true;
                String originalValue = (String) tableModel.getValueAt(i, 1);
                String variableName = (String) tableModel.getValueAt(i, 2);
                
                traverseAndReplace(treeModel.getRoot(), originalValue, "${" + variableName + "}", treeModel);
            }
        }

        if (!anySelected) {
            JOptionPane.showMessageDialog(this, "Please select at least one variable to correlate.");
            return;
        }

        injectUserDefinedVariables(treeModel);

        SwingUtilities.invokeLater(() -> {
            try {
                if (guiPackage.getCurrentGui() != null && guiPackage.getTreeListener().getCurrentNode() != null) {
                    guiPackage.getCurrentGui().configure(guiPackage.getTreeListener().getCurrentNode().getTestElement());
                }
                guiPackage.getMainFrame().repaint();
                JOptionPane.showMessageDialog(CorrelatorDialog.this, "Correlation complete! Replaced in all requests.");
            } catch (Exception ex) {
                System.err.println("GUI refresh warning: " + ex.getMessage());
            }
        });
    }

    private void traverseAndReplace(Object node, String target, String replacement, JMeterTreeModel treeModel) {
        JMeterTreeNode treeNode = (JMeterTreeNode) node;
        boolean nodeChanged = false;
        TestElement element = treeNode.getTestElement();

        PropertyIterator iter = element.propertyIterator();
        while (iter.hasNext()) {
            JMeterProperty prop = iter.next();
            if (replaceInProperty(prop, target, replacement, element)) {
                nodeChanged = true;
            }
        }

        if (nodeChanged) {
            treeModel.nodeChanged(treeNode); 
        }

        for (int i = 0; i < treeNode.getChildCount(); i++) {
            traverseAndReplace(treeNode.getChildAt(i), target, replacement, treeModel);
        }
    }

    private boolean replaceInProperty(JMeterProperty prop, String target, String replacement, TestElement parentElement) {
        boolean changed = false;

        if (prop instanceof StringProperty) {
            String value = prop.getStringValue();
            if (value != null && value.contains(target)) {
                String newValue = value.replace(target, replacement);
                
                if (parentElement != null && prop.getName() != null && !prop.getName().isEmpty()) {
                    parentElement.setProperty(prop.getName(), newValue);
                } else {
                    prop.setObjectValue(newValue);
                }
                changed = true;
            }
        } else if (prop instanceof MultiProperty) {
            PropertyIterator iter = ((MultiProperty) prop).iterator();
            while (iter.hasNext()) {
                if (replaceInProperty(iter.next(), target, replacement, null)) {
                    changed = true;
                }
            }
        } else if (prop instanceof TestElementProperty) {
            TestElement te = ((TestElementProperty) prop).getElement();
            if (te != null) {
                PropertyIterator iter = te.propertyIterator();
                while (iter.hasNext()) {
                    if (replaceInProperty(iter.next(), target, replacement, te)) {
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private void injectUserDefinedVariables(JMeterTreeModel treeModel) {
        Arguments udv = new Arguments();
        udv.setName("Auto-Generated UUID Variables");
        udv.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
        udv.setProperty(TestElement.GUI_CLASS, ArgumentsPanel.class.getName());

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean isSelected = (Boolean) tableModel.getValueAt(i, 0);
            if (isSelected != null && isSelected) {
                String variableName = (String) tableModel.getValueAt(i, 2);
                udv.addArgument(variableName, "${__UUID()}");
            }
        }

        try {
            JMeterTreeNode hiddenRoot = (JMeterTreeNode) treeModel.getRoot();
            JMeterTreeNode testPlanNode = null;
            
            for (int i = 0; i < hiddenRoot.getChildCount(); i++) {
                JMeterTreeNode child = (JMeterTreeNode) hiddenRoot.getChildAt(i);
                if (child.getTestElement().getClass().getName().contains("TestPlan")) {
                    testPlanNode = child;
                    break;
                }
            }
            if (testPlanNode == null) testPlanNode = (JMeterTreeNode) hiddenRoot.getChildAt(0);
            
            treeModel.addComponent(udv, testPlanNode);
            
        } catch (Exception e) {
            System.err.println("Error inserting UDV node: " + e.getMessage());
        }
    }
}