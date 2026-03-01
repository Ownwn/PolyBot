import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;

public class TradingPanel extends JPanel {
    private final JTable tradesTable;
    private final DefaultTableModel tableModel;

    public TradingPanel() {
        setLayout(new BorderLayout());

        // top panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh Trades");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 24));
        refreshButton.addActionListener(e -> refreshTrades());
        topPanel.add(refreshButton);
        add(topPanel, BorderLayout.NORTH);

        // trades table
        String[] columnNames = { "Time", "Market", "Side", "Price", "Current", "Size", "Total", "P/L", "ID" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tradesTable = new JTable(tableModel);
        tradesTable.setFont(new Font("Arial", Font.PLAIN, 18));
        tradesTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 20));
        tradesTable.setRowHeight(40);
        tradesTable.getTableHeader().setReorderingAllowed(false);

        tradesTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                String side = (String) table.getValueAt(row, 2);
                if (!isSelected) {
                    if ("BUY".equalsIgnoreCase(side)) {
                        c.setBackground(new Color(235, 255, 235));
                    } else if ("SELL".equalsIgnoreCase(side)) {
                        c.setBackground(new Color(255, 235, 235));
                    } else {
                        c.setBackground(table.getBackground());
                    }
                } else {
                    c.setBackground(table.getSelectionBackground());
                }

                if (column == 7 && value instanceof String valStr) {
                    if (valStr.startsWith("+")) {
                        c.setForeground(new Color(0, 150, 0));
                    } else if (valStr.startsWith("-")) {
                        c.setForeground(Color.RED);
                    } else {
                        c.setForeground(table.getForeground());
                    }
                } else {
                    c.setForeground(table.getForeground());
                }

                return c;
            }
        });

        tradesTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Time
        tradesTable.getColumnModel().getColumn(1).setPreferredWidth(250); // Market
        tradesTable.getColumnModel().getColumn(2).setPreferredWidth(70); // Side
        tradesTable.getColumnModel().getColumn(3).setPreferredWidth(80); // Price
        tradesTable.getColumnModel().getColumn(4).setPreferredWidth(80); // Current
        tradesTable.getColumnModel().getColumn(5).setPreferredWidth(80); // Size
        tradesTable.getColumnModel().getColumn(6).setPreferredWidth(100); // Total
        tradesTable.getColumnModel().getColumn(7).setPreferredWidth(100); // P/L
        tradesTable.getColumnModel().getColumn(8).setPreferredWidth(100); // ID

        JScrollPane scrollPane = new JScrollPane(tradesTable);
        add(scrollPane, BorderLayout.CENTER);

        refreshTrades();
    }

    private void refreshTrades() {
        new Thread(() -> {
            try {
                JsonArray trades = Http.getJsonArray("http://localhost:3000/trades");
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    if (trades != null) {
                        for (Json tradeElement : trades.elements()) {
                            if (tradeElement instanceof JsonObject trade) {
                                Vector<Object> row = new Vector<>();

                                String time = trade.getString("match_time");
                                if (time == null)
                                    time = trade.getString("time");
                                if (time == null)
                                    time = trade.getString("timestamp");
                                row.add(time != null ? formatTime(time) : "N/A");

                                String marketName = trade.getString("market_name");
                                row.add(marketName != null ? marketName : "N/A");

                                String side = trade.getString("side");
                                row.add(side != null ? (side.toUpperCase()) : "N/A");

                                String priceStr = trade.getString("price");
                                double price = 0;
                                if (priceStr != null) {
                                    try {
                                        price = Double.parseDouble(priceStr);
                                        row.add(String.format("%.3f", price));
                                    } catch (Exception e) {
                                        row.add(priceStr);
                                    }
                                } else {
                                    row.add("N/A");
                                }

                                Json currentPriceJson = trade.get("current_price");
                                double currentPrice = -1.0;
                                if (currentPriceJson != null && !(currentPriceJson instanceof JsonNull)) {
                                    currentPrice = trade.getDouble("current_price");
                                }
                                row.add(currentPrice >= 0 ? String.format("%.3f", currentPrice) : "N/A");

                                String sizeStr = trade.getString("size");
                                double size = 0;
                                if (sizeStr != null) {
                                    try {
                                        size = Double.parseDouble(sizeStr);
                                        row.add(String.format("%.2f", size));
                                    } catch (Exception e) {
                                        row.add(sizeStr);
                                    }
                                } else {
                                    row.add("N/A");
                                }

                                if (price > 0 && size > 0) {
                                    row.add(String.format("$%.2f", price * size));
                                } else {
                                    row.add("N/A");
                                }

                                if (price > 0 && currentPrice >= 0 && size > 0) {
                                    double pl;
                                    if ("BUY".equalsIgnoreCase(side)) {
                                        pl = (currentPrice - price) * size;
                                    } else {
                                        pl = (price - currentPrice) * size;
                                    }
                                    String plStr = String.format("%+.2f", pl);
                                    row.add(plStr);
                                } else {
                                    row.add("N/A");
                                }

                                String id = trade.getString("id");
                                if (id != null && id.length() > 8) {
                                    id = id.substring(0, 8);
                                }
                                row.add(id != null ? id : "N/A");

                                tableModel.addRow(row);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Error fetching trades: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private String formatTime(String rawTime) {
        try {
            if (rawTime.contains("T")) {
                String[] parts = rawTime.split("T");
                if (parts.length > 1) {
                    String timePart = parts[1];
                    if (timePart.contains(".")) {
                        return timePart.substring(0, timePart.indexOf("."));
                    }
                    if (timePart.endsWith("Z")) {
                        return timePart.substring(0, timePart.length() - 1);
                    }
                    return timePart;
                }
            }
            long ts = Long.parseLong(rawTime);
            if (ts < 100000000000L)
                ts *= 1000;
            return new java.util.Date(ts).toString().substring(11, 19); // HH:mm:ss
        } catch (Exception e) {
            return rawTime;
        }
    }
}
