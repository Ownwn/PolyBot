import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;

public class TradingPanel extends JPanel {
    private final JTable tradesTable;
    private final DefaultTableModel tableModel;

    private OrderBookPoller botPoller;
    private TradingBot activeBot;
    private javax.swing.Timer pnlTimer;
    private final JButton toggleBotBtn;
    private final JTextField tokenField;
    private final JLabel pnlLabel;
    private final JTextArea logArea;

    public TradingPanel() {
        setLayout(new BorderLayout());

        // top panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        JButton refreshButton = new JButton("Refresh Trades");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 24));
        refreshButton.addActionListener(e -> refreshPositions());
        topPanel.add(refreshButton);

        // Bot controls
        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var titleBorder = BorderFactory.createTitledBorder("Trading bot");
        titleBorder.setTitleFont(new Font("Arial", Font.BOLD, 24));
        botPanel.setBorder(titleBorder);

        JLabel idOrSlugLabel = new JLabel("Token ID or Slug:");
        idOrSlugLabel.setFont(new Font("Arial", Font.BOLD, 20));
        botPanel.add(idOrSlugLabel);
        tokenField = new JTextField("gta-6-launch-postponed-again", 40) {
            {
                setFont(new Font("Arial", Font.BOLD, 18));
            }
        };
        botPanel.add(tokenField);

        toggleBotBtn = new JButton("Start Bot");
        toggleBotBtn.setFont(new Font("Arial", Font.BOLD, 18));
        toggleBotBtn.setBackground(new Color(200, 255, 200));
        toggleBotBtn.addActionListener(e -> toggleBot());
        botPanel.add(toggleBotBtn);

        pnlLabel = new JLabel("  Simulated PnL: $0.00  ");
        pnlLabel.setFont(new Font("Arial", Font.BOLD, 18));
        botPanel.add(pnlLabel);

        topPanel.add(botPanel);

        add(topPanel, BorderLayout.NORTH);

        // trades table
        String[] columnNames = { "Token", "Size", "Entry Price", "Current Price", "Unrealized P/L" };
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

        tradesTable.getColumnModel().getColumn(0).setPreferredWidth(250); // Token
        tradesTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Size
        tradesTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Entry Price
        tradesTable.getColumnModel().getColumn(3).setPreferredWidth(150); // Current Price
        tradesTable.getColumnModel().getColumn(4).setPreferredWidth(150); // Unrealized P/L

        JScrollPane scrollPane = new JScrollPane(tradesTable);

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 20));
        logArea.setBackground(new Color(245, 245, 245));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Bot Logs"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, logScroll);
        splitPane.setResizeWeight(0.6); // 60% table
        add(splitPane, BorderLayout.CENTER);

        refreshPositions();
    }

    private void toggleBot() {
        if (botPoller != null) {
            botPoller.stop();
            botPoller = null;
            if (pnlTimer != null) {
                pnlTimer.stop();
                pnlTimer = null;
            }
            toggleBotBtn.setText("Start Bot");
            toggleBotBtn.setBackground(new Color(200, 255, 200));
            tokenField.setEnabled(true);
            System.out.println("bot stopped.");
        } else {
            String input = tokenField.getText().trim();
            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a Token ID or Market Slug");
                return;
            }

            // if input is a long number it's probs a Token ID
            final String token;
            if (input.matches("\\d{20,}")) {
                token = input;
            } else {
                try {
                    JsonObject market = Http.getJsonObject("https://gamma-api.polymarket.com/markets/slug/" + input);

                    Json closedJson = market.get("closed");
                    if (closedJson instanceof JsonBoolean jb && jb.inner()) {
                        JOptionPane.showMessageDialog(this, "Market is already closed and has no active order book.");
                        return;
                    }

                    Json activeJson = market.get("active");
                    if (activeJson instanceof JsonBoolean jb && !jb.inner()) {
                        JOptionPane.showMessageDialog(this, "Market is inactive and cannot be traded.");
                        return;
                    }

                    Json tokenData = market.get("clobTokenIds");
                    JsonArray tokens = null;
                    if (tokenData instanceof JsonArray ja) {
                        tokens = ja;
                    } else if (tokenData instanceof JsonString js) {
                        Json parsed = Json.parse(js.inner());
                        if (parsed instanceof JsonArray ja)
                            tokens = ja;
                    }

                    if (tokens == null || tokens.elements().isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Could not resolve Token ID for slug: " + input);
                        return;
                    }
                    token = ((JsonString) tokens.elements().get(0)).inner();
                    System.out.println("resolved slug '" + input + "' to token: " + token);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error fetching from Gamma API: " + ex.getMessage());
                    return;
                }
            }

            activeBot = new TradingBot();
            activeBot.setLogListener(message -> {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(message + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            });
            double buyThreshold = 0.30;
            double sellThreshold = -0.30;
            double tradeSize = 10.0;

            QuantStrategy strategy = new QuantStrategy(activeBot, token, buyThreshold, sellThreshold, tradeSize);
            botPoller = new OrderBookPoller(activeBot, token, strategy, 1000);
            botPoller.start();

            pnlTimer = new javax.swing.Timer(1000, e -> {
                if (activeBot != null) {
                    double pnl = activeBot.getSimulatedPnL();
                    pnlLabel.setText(String.format("  Simulated PnL: $%+.2f  ", pnl));
                    if (pnl > 0)
                        pnlLabel.setForeground(new Color(0, 150, 0));
                    else if (pnl < 0)
                        pnlLabel.setForeground(Color.RED);
                    else
                        pnlLabel.setForeground(Color.BLACK);

                    refreshPositions();
                }
            });
            pnlTimer.start();

            toggleBotBtn.setText("Stop Bot");
            toggleBotBtn.setBackground(new Color(255, 200, 200));
            tokenField.setEnabled(false);
            System.out.println("bot started for token: " + token);
        }
    }

    private void refreshPositions() {
        if (activeBot == null) {
            SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));
            return;
        }

        new Thread(() -> {
            try {
                var positions = activeBot.getSimPositions();
                var entryPrices = activeBot.getSimEntryPrices();

                Vector<Vector<Object>> newRows = new Vector<>();

                for (String tokenId : positions.keySet()) {
                    double size = positions.get(tokenId);
                    if (size <= 0)
                        continue;

                    double entryPrice = entryPrices.getOrDefault(tokenId, 0.0);

                    double currentPrice = 0.0;
                    JsonObject orderBook = activeBot.getOrderBook(tokenId);
                    if (orderBook != null && orderBook.get("bids") instanceof JsonArray bids
                            && !bids.elements().isEmpty()) {
                        JsonObject bestBidObj = (JsonObject) bids.elements().get(bids.elements().size() - 1);
                        try {
                            currentPrice = Double.parseDouble(bestBidObj.getString("price"));
                        } catch (Exception ignored) {
                        }
                    }

                    double unrealizedPnL = (currentPrice - entryPrice) * size;

                    Vector<Object> row = new Vector<>();
                    row.add(tokenId.length() > 20 ? tokenId.substring(0, 20) + "..." : tokenId);
                    row.add(String.format("%.2f", size));
                    row.add(String.format("$%.3f", entryPrice));
                    row.add(currentPrice > 0 ? String.format("$%.3f", currentPrice) : "N/A");
                    row.add(currentPrice > 0 ? String.format("$%+.2f", unrealizedPnL) : "N/A");

                    newRows.add(row);
                }

                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    for (Vector<Object> row : newRows) {
                        tableModel.addRow(row);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
