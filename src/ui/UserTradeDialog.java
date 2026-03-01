
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class UserTradeDialog {
    public static void show(JFrame parent, String user, List<Transaction> trades) {
        JDialog dialog = new JDialog(parent, "Trades for " + user, true);
        dialog.setLayout(new BorderLayout());

        String[] cols = { "Time", "Side", "Instrument", "Value", "Action" };
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;
            }
        };

        for (var t : trades) {
            model.addRow(new Object[] {
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(t.timestamp())),
                    t.side(),
                    t.title(),
                    String.format("$%.2f", t.value()),
                    "VIEW"
            });
        }

        JTable table = new JTable(model);
        table.setFont(new Font("Arial", Font.PLAIN, 24));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 26));
        table.setRowHeight(45);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String side = (String) table.getValueAt(row, 1);

                if (!isSelected) {
                    if ("BUY".equals(side)) {
                        c.setBackground(new Color(235, 255, 235));
                    } else if ("SELL".equals(side)) {
                        c.setBackground(new Color(255, 235, 235));
                    } else {
                        c.setBackground(table.getBackground());
                    }
                } else {
                    c.setBackground(table.getSelectionBackground());
                }

                if ("SELL".equals(side)) {
                    c.setForeground(new Color(200, 0, 0));
                } else if ("BUY".equals(side)) {
                    c.setForeground(new Color(0, 150, 0));
                } else {
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });

        table.getColumn("Action").setCellRenderer(new ButtonRenderer());
        table.getColumn("Action").setCellEditor(new ButtonEditor(new JCheckBox(), v -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                Transaction t = trades.get(row);
                JLink.openWebpage("https://polymarket.com/market/" + t.slug());
            }
        }));

        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setSize(1200, 800);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    static class ButtonRenderer extends JButton implements javax.swing.table.TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }

    static class ButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private java.util.function.Consumer<Void> action;

        public ButtonEditor(JCheckBox checkBox, java.util.function.Consumer<Void> action) {
            super(checkBox);
            this.action = action;
            button = new JButton();
            button.setOpaque(true);
            button.addActionListener(e -> fireEditingStopped());
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
                int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);
            return button;
        }

        public Object getCellEditorValue() {
            action.accept(null);
            return label;
        }
    }
}
