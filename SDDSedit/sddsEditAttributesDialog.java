/*************************************************************************\
* Copyright (c) 2002 The University of Chicago, as Operator of Argonne
* National Laboratory.
* Copyright (c) 2002 The Regents of the University of California, as
* Operator of Los Alamos National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution. 
\*************************************************************************/

package SDDS.java.SDDSedit;

import javax.swing.*;
import java.beans.*;
import java.awt.*;
import java.awt.event.*;
import SDDS.java.SDDS.*;

class sddsEditAttributesDialog extends JDialog {
    private String name = null;
    private String symbol = null;
    private String units = null;
    private String description = null;
    private String format_string = null;
    private String field_length = "0";
    private String fixed_value = null;
    private String group_name = null;
    private int type = 5;
    private int dimensions = 1;
    private int modetype = 1;
    private JOptionPane optionPane;
    boolean attributes = false;
    final JTextField[] fields;
    final JRadioButton[] typeButtons;
    final JLabel[] labels;

    public void setAttributesMode(int mode) {
        attributes = true;
        // name = symbol = units = description = format_string = fixed_value = null;
        // field_length = "0";
        int i;
        for (i = 0; i < 10; i++)
            typeButtons[i].setEnabled(false);
        if (mode == 1) {
            setTitle("Parameter Info");
            labels[5].setText("fixed value:");
            fields[5].setText(null);
        } else if (mode == 2) {
            setTitle("Column Info");
            labels[5].setText("field length:");
            fields[5].setText("0");
        } else if (mode == 3) {
            setTitle("Array Info");
            labels[5].setText("field length:");
            fields[5].setText("0");
        }
        modetype = mode;
    }

    public void setName(String n) {
        fields[0].setText(n);
    }

    public void setSymbol(String s) {
        fields[1].setText(s);
    }

    public void setUnits(String u) {
        fields[2].setText(u);
    }

    public void setDescription(String d) {
        fields[3].setText(d);
    }

    public void setFormatString(String fs) {
        fields[4].setText(fs);
    }

    public void setFieldLength(int fl) {
        fields[5].setText(String.valueOf(fl));
    }

    public void setFixedValue(String fv) {
        fields[5].setText(fv);
    }

    public void setGroupName(String gn) {
        fields[6].setText(gn);
    }

    // public void setDimensions(int d) {
    // fields[7].setText(String.valueOf(d));
    // }
    public void setType(int t) {
        if ((t > 0) && (t < 11))
            typeButtons[t - 1].setSelected(true);
        else
            typeButtons[8].setSelected(true);
    }

    public void setInsertMode(int mode) {
        attributes = false;
        int i;
        for (i = 0; i < 10; i++)
            typeButtons[i].setEnabled(true);
        if (mode == 1) {
            setTitle("New Parameter");
            labels[5].setText("fixed value:");
            fields[5].setText(null);
        } else if (mode == 2) {
            setTitle("New Column");
            labels[5].setText("field length:");
            fields[5].setText("0");
        } else if (mode == 3) {
            setTitle("New Array");
            labels[5].setText("field length:");
            fields[5].setText("0");
            fields[6].setText("");
        }
        modetype = mode;
    }

    public String getName() {
        if ((name == null) || (name.length() == 0))
            return null;
        return name;
    }

    public String getSymbol() {
        if (symbol.length() == 0)
            return null;
        return symbol;
    }

    public String getUnits() {
        if (units.length() == 0)
            return null;
        return units;
    }

    public String getDescription() {
        if (description.length() == 0)
            return null;
        return description;
    }

    public String getFormatString() {
        if (format_string.length() == 0)
            return null;
        return format_string;
    }

    public int getFieldLength() {
        int fl;
        try {
            fl = (Integer.valueOf(field_length)).intValue();
        } catch (NumberFormatException e) {
            fl = 0;
        }
        return fl;
    }

    public String getFixedValue() {
        if (fixed_value.length() == 0)
            return null;
        return fixed_value;
    }

    public String getGroupName() {
        if (group_name.length() == 0)
            return null;
        return group_name;
    }

    public int getDimensions() {
        return dimensions;
    }

    public int getSDDSType() {
        return type;
    }

    public sddsEditAttributesDialog(Frame aFrame, int mode) {
        super(aFrame, true);
        // final DialogDemo dd = parent;
        modetype = mode;
        if (modetype == 1) {
            if (attributes)
                setTitle("Parameter Info");
            else
                setTitle("New Parameter");
        } else if (modetype == 2) {
            if (attributes)
                setTitle("Column Info");
            else
                setTitle("New Column");
        } else {
            if (attributes)
                setTitle("Array Info");
            else
                setTitle("New Array");
        }
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel panel1 = new JPanel(gbl);

        labels = new JLabel[9];
        fields = new JTextField[8];

        labels[0] = new JLabel("name:", JLabel.LEFT);
        labels[0].setForeground(Color.black);
        gbc.ipadx = 10;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[0], gbc);
        panel1.add(labels[0]);
        fields[0] = new JTextField(10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[0], gbc);
        panel1.add(fields[0]);

        labels[1] = new JLabel("symbol:", JLabel.LEFT);
        labels[1].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[1], gbc);
        panel1.add(labels[1]);
        fields[1] = new JTextField(10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[1], gbc);
        panel1.add(fields[1]);

        labels[2] = new JLabel("units:", JLabel.LEFT);
        labels[2].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[2], gbc);
        panel1.add(labels[2]);
        fields[2] = new JTextField(10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[2], gbc);
        panel1.add(fields[2]);

        labels[3] = new JLabel("description:", JLabel.LEFT);
        labels[3].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[3], gbc);
        panel1.add(labels[3]);
        fields[3] = new JTextField(10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[3], gbc);
        panel1.add(fields[3]);

        labels[4] = new JLabel("format string:", JLabel.LEFT);
        labels[4].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[4], gbc);
        panel1.add(labels[4]);
        fields[4] = new JTextField(10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[4], gbc);
        panel1.add(fields[4]);

        labels[5] = new JLabel("field length:", JLabel.LEFT);
        labels[5].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[5], gbc);
        panel1.add(labels[5]);
        fields[5] = new JTextField("0", 10);
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(fields[5], gbc);
        panel1.add(fields[5]);

        labels[6] = new JLabel("type:", JLabel.LEFT);

        if (modetype == 3) {

            labels[7] = new JLabel("group_name:", JLabel.LEFT);
            labels[7].setForeground(Color.black);
            gbc.weightx = 0.0;
            gbc.gridwidth = 1;
            gbl.setConstraints(labels[7], gbc);
            panel1.add(labels[7]);
            fields[6] = new JTextField("0", 10);
            gbc.weightx = 1.0;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbl.setConstraints(fields[6], gbc);
            panel1.add(fields[6]);

            /*
             * labels[8] = new JLabel("dimensions:", JLabel.LEFT);
             * labels[8].setForeground(Color.black);
             * gbc.weightx = 0.0;
             * gbc.gridwidth = 1;
             * gbl.setConstraints(labels[8], gbc);
             * panel1.add(labels[8]);
             * fields[7] = new JTextField("0",10);
             * gbc.weightx = 1.0;
             * gbc.gridwidth = gbc.REMAINDER;
             * gbl.setConstraints(fields[7], gbc);
             * panel1.add(fields[7]);
             */
        }

        // labels[6] = new JLabel("type:", JLabel.LEFT);
        labels[6].setForeground(Color.black);
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbl.setConstraints(labels[6], gbc);
        panel1.add(labels[6]);

        typeButtons = new JRadioButton[10];
        typeButtons[0] = new JRadioButton("double");
        typeButtons[1] = new JRadioButton("float");
        typeButtons[2] = new JRadioButton("long64");
        typeButtons[3] = new JRadioButton("ulong64");
        typeButtons[4] = new JRadioButton("long");
        typeButtons[5] = new JRadioButton("ulong");
        typeButtons[6] = new JRadioButton("short");
        typeButtons[7] = new JRadioButton("ushort");
        typeButtons[8] = new JRadioButton("string");
        typeButtons[9] = new JRadioButton("character");
        ButtonGroup typeButtonGroup = new ButtonGroup();
        typeButtonGroup.add(typeButtons[6]);
        typeButtonGroup.add(typeButtons[7]);
        typeButtonGroup.add(typeButtons[4]);
        typeButtonGroup.add(typeButtons[5]);
        typeButtonGroup.add(typeButtons[2]);
        typeButtonGroup.add(typeButtons[3]);
        typeButtonGroup.add(typeButtons[1]);
        typeButtonGroup.add(typeButtons[0]);
        typeButtonGroup.add(typeButtons[8]);
        typeButtonGroup.add(typeButtons[9]);
        typeButtons[8].setSelected(true);

        gbc.weightx = 1.0;
        gbl.setConstraints(typeButtons[6], gbc);
        panel1.add(typeButtons[6]);
        gbl.setConstraints(typeButtons[7], gbc);
        panel1.add(typeButtons[7]);
        gbl.setConstraints(typeButtons[4], gbc);
        panel1.add(typeButtons[4]);
        gbl.setConstraints(typeButtons[5], gbc);
        panel1.add(typeButtons[5]);
        gbl.setConstraints(typeButtons[2], gbc);
        panel1.add(typeButtons[2]);
        gbl.setConstraints(typeButtons[3], gbc);
        panel1.add(typeButtons[3]);
        gbl.setConstraints(typeButtons[1], gbc);
        panel1.add(typeButtons[1]);
        gbl.setConstraints(typeButtons[0], gbc);
        panel1.add(typeButtons[0]);
        gbl.setConstraints(typeButtons[8], gbc);
        panel1.add(typeButtons[8]);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbl.setConstraints(typeButtons[9], gbc);
        panel1.add(typeButtons[9]);

        Object[] array = { panel1 };

        final String btnString1 = "OK";
        final String btnString2 = "Cancel";
        Object[] options = { btnString1, btnString2 };

        optionPane = new JOptionPane(array,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                options,
                options[0]);
        setContentPane(optionPane);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                /*
                 * Instead of directly closing the window,
                 * we're going to change the JOptionPane's
                 * value property.
                 */
                optionPane.setValue(Integer.valueOf(
                        JOptionPane.CLOSED_OPTION));
            }
        });
        /*
         * fields[0].addActionListener(new ActionListener() {
         * public void actionPerformed(ActionEvent e) {
         * optionPane.setValue(btnString1);
         * }
         * });
         */
        optionPane.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                String prop = e.getPropertyName();

                if (isVisible()
                        && (e.getSource() == optionPane)
                        && (prop.equals(JOptionPane.VALUE_PROPERTY) ||
                                prop.equals(JOptionPane.INPUT_VALUE_PROPERTY))) {
                    Object value = optionPane.getValue();

                    if (value == JOptionPane.UNINITIALIZED_VALUE) {
                        // ignore reset
                        return;
                    }

                    // Reset the JOptionPane's value.
                    // If you don't do this, then if the user
                    // presses the same button next time, no
                    // property change event will be fired.
                    optionPane.setValue(
                            JOptionPane.UNINITIALIZED_VALUE);

                    if (value.equals(btnString1)) {
                        name = fields[0].getText();
                        symbol = fields[1].getText();
                        units = fields[2].getText();
                        description = fields[3].getText();
                        format_string = fields[4].getText();
                        field_length = fixed_value = fields[5].getText();
                        if (modetype == 3) {
                            group_name = fields[6].getText();
                            // dimensions = (Long.valueOf(fields[7].getText())).intValue();
                        }
                        int i;
                        for (i = 0; i < 10; i++) {
                            if (typeButtons[i].isSelected()) {
                                type = i + 2;
                                break;
                            }
                        }
                        if (SDDSUtil.isValidName(name)) {
                            // we're done; dismiss the dialog
                            setVisible(false);
                        } else {
                            // text was invalid
                            fields[0].selectAll();
                            JOptionPane.showMessageDialog(
                                    sddsEditAttributesDialog.this,
                                    "Sorry, \"" + name + "\" "
                                            + "isn't a valid name.\n",
                                    "Try again",
                                    JOptionPane.ERROR_MESSAGE);
                            name = null;
                        }
                    } else { // user closed dialog or clicked cancel
                        name = null;
                        setVisible(false);
                    }
                }
            }
        });
    }
}
