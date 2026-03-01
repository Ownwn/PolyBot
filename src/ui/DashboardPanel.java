
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class DashboardPanel extends JPanel {
    private final DefaultTableModel buyModel = new DefaultTableModel(
            new Object[] { "Instrument", "Buy Vol ($)", "slug" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel sellModel = new DefaultTableModel(
            new Object[] { "Instrument", "Sell Vol ($)", "slug" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel spenderModel = new DefaultTableModel(
            new Object[] { "User (Wallet)", "Hourly Volume ($)" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel smartModel = new DefaultTableModel(
            new Object[] { "Smart Trader", "PnL ($)", "Vol ($)", "address" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea logArea = new JTextArea(15, 60);
    private final java.util.function.BiConsumer<String, String> userTradeOpener;
    private final java.util.function.Consumer<String> linkOpener;

    public DashboardPanel(java.util.function.BiConsumer<String, String> userTradeOpener,
            java.util.function.Consumer<String> linkOpener) {
        this.userTradeOpener = userTradeOpener;
        this.linkOpener = linkOpener;
        setLayout(new BorderLayout(0, 15));

        JPanel centerPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        setupTable(new JTable(buyModel), "Top Bought (1h) - Double click to visit", centerPanel, true);
        setupTable(new JTable(sellModel), "Top Sold (1h) - Double click to visit", centerPanel, true);
        setupSpenderTable(centerPanel);
        setupSmartTable(centerPanel);

        add(centerPanel, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 200, 0));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 20));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Live Trade Log", 0, 0,
                new Font("Arial", Font.BOLD, 18)));
        add(logScroll, BorderLayout.SOUTH);
    }

    private void setupTable(JTable table, String title, JPanel parent, boolean isMarketTable) {
        table.setFont(new Font("Arial", Font.PLAIN, 18));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 20));
        table.setRowHeight(40);
        table.removeColumn(table.getColumnModel().getColumn(2));

        boolean isBought = title.toLowerCase().contains("bought");
        Color textColor = isBought ? new Color(0, 150, 0) : new Color(200, 0, 0);
        Color bgColor = isBought ? new Color(235, 255, 235) : new Color(255, 235, 235);

        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setForeground(textColor);
                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                } else {
                    c.setBackground(bgColor);
                }
                return c;
            }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        String slug = (String) table.getModel().getValueAt(row, 2);
                        linkOpener.accept("https://polymarket.com/market/" + slug);
                    }
                }
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, 0, 0,
                new Font("Arial", Font.BOLD, 18)));
        parent.add(scroll);
    }

    private void setupSpenderTable(JPanel parent) {
        JTable table = new JTable(spenderModel);
        table.setFont(new Font("Arial", Font.PLAIN, 18));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 20));
        table.setRowHeight(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row == -1)
                    return;
                String user = (String) spenderModel.getValueAt(row, 0);
                userTradeOpener.accept(user, "click");
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Top Spenders (1h) - Click for trades", 0, 0, new Font("Arial", Font.BOLD, 18)));
        parent.add(scroll);
    }

    private void setupSmartTable(JPanel parent) {
        JTable table = new JTable(smartModel);
        table.setFont(new Font("Arial", Font.PLAIN, 18));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 20));
        table.setRowHeight(40);
        table.removeColumn(table.getColumnModel().getColumn(3));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row == -1)
                    return;
                String user = (String) smartModel.getValueAt(row, 0);
                String addr = (String) smartModel.getValueAt(row, 3);
                if (e.getClickCount() == 1)
                    userTradeOpener.accept(user, addr);
                else if (e.getClickCount() == 2)
                    linkOpener.accept("https://polymarket.com/profile/" + addr);
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Smart Traders (Weekly Profitable) - Double Click Profile", 0, 0, new Font("Arial", Font.BOLD, 18)));
        parent.add(scroll);
    }

    public void updateData(List<Map.Entry<String, Object[]>> buys, List<Map.Entry<String, Object[]>> sells,
            List<Map.Entry<String, Double>> spenders, List<Object[]> smarts) {
        buyModel.setRowCount(0);
        for (var e : buys)
            buyModel.addRow(e.getValue());
        sellModel.setRowCount(0);
        for (var e : sells)
            sellModel.addRow(e.getValue());
        spenderModel.setRowCount(0);
        for (var e : spenders)
            spenderModel.addRow(new Object[] { e.getKey(), String.format("%.2f", e.getValue()) });
        smartModel.setRowCount(0);
        for (var s : smarts)
            smartModel.addRow(s);
    }

    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            if (logArea.getLineCount() > 100) {
                try {
                    int end = logArea.getLineStartOffset(1);
                    logArea.replaceRange("", 0, end);
                } catch (Exception ignored) {
                }
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
