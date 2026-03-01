
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;

public class DeepDivePanel extends JPanel {
    private final JComboBox<String> marketSelector = new JComboBox<>();
    private final Map<String, String> titleToSlug = new HashMap<>();
    private final JLabel deepDiveLabel = new JLabel("Select a market to deep dive", SwingConstants.CENTER);
    private final DefaultTableModel orderBookModel = new DefaultTableModel(new Object[] { "Price", "Size", "Side" }, 0);
    private final DefaultTableModel volumePriceModel = new DefaultTableModel(
            new Object[] { "Price Level", "Total Volume ($)" }, 0);
    private final JLabel liquidityLabel = new JLabel("Liquidity: ?", SwingConstants.CENTER) {
        {
            setFont(new Font("Arial", Font.BOLD, 24));
        }
    };

    private final JLabel spreadLabel = new JLabel("Spread: ?", SwingConstants.CENTER) {
        {
            setFont(new Font("Arial", Font.BOLD, 24));
        }
    };
    private final JLabel priceLabel = new JLabel("Last Price: ?", SwingConstants.CENTER) {
        {
            setFont(new Font("Arial", Font.BOLD, 24));
        }
    };
    private final JLabel sentimentLabel = new JLabel("Sentiment: ?", SwingConstants.CENTER) {
        {
            setFont(new Font("Arial", Font.BOLD, 24));
        }
    };

    private final JLabel loadingStatusLabel = new JLabel("") {
        {
            setFont(new Font("Arial", Font.BOLD, 32));
            setForeground(Color.RED);
        }
    };

    public DeepDivePanel() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel topSelection = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        marketSelector.setFont(new Font("Arial", Font.PLAIN, 24));
        marketSelector.setPreferredSize(new Dimension(800, 50));

        JButton loadBtn = new JButton("Load Market Stats");
        loadBtn.setFont(new Font("Arial", Font.BOLD, 24));
        loadBtn.addActionListener(e -> {
            String selected = (String) marketSelector.getSelectedItem();
            String slug = titleToSlug.get(selected);
            if (slug != null) {
                deepDiveLabel.setText("Loading: " + selected + "...");
                liquidityLabel.setText("Liquidity: ?");
                sentimentLabel.setText("Sentiment: ?");
                spreadLabel.setText("Spread: ?");
                priceLabel.setText("Last Price: ?");
                loadingStatusLabel.setText("");
                new Thread(() -> loadRealData(selected, slug)).start();
            }
        });

        JButton viewBtn = new JButton("View on Polymarket");
        viewBtn.setFont(new Font("Arial", Font.BOLD, 24));
        viewBtn.addActionListener(e -> {
            String selected = (String) marketSelector.getSelectedItem();
            String slug = titleToSlug.get(selected);
            if (slug != null) {
                JLink.openWebpage("https://polymarket.com/market/" + slug);
            }
        });

        topSelection.add(new JLabel("Select Market:") {
            {
                setFont(new Font("Arial", Font.BOLD, 24));
            }
        });
        topSelection.add(marketSelector);
        topSelection.add(loadBtn);
        topSelection.add(viewBtn);

        topSelection.add(loadingStatusLabel);
        add(topSelection, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new GridLayout(1, 3, 20, 20));

        // Order Book
        JTable obTable = new JTable(orderBookModel);
        obTable.setFont(new Font("Arial", Font.PLAIN, 20));
        obTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 22));
        obTable.setRowHeight(40);
        obTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String side = (String) table.getValueAt(row, 2);
                if ("ASK".equals(side)) {
                    c.setForeground(new Color(200, 0, 0));
                    if (!isSelected)
                        c.setBackground(new Color(255, 235, 235));
                } else if ("BID".equals(side)) {
                    c.setForeground(new Color(0, 150, 0)); // Darker green
                    if (!isSelected)
                        c.setBackground(new Color(235, 255, 235));
                } else {
                    c.setForeground(Color.BLACK);
                    if (!isSelected)
                        c.setBackground(table.getBackground());
                }
                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                }
                return c;
            }
        });
        JScrollPane obScroll = new JScrollPane(obTable);
        obScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Order Book (Bids/Asks)", 0, 0, new Font("Arial", Font.BOLD, 22)));
        contentPanel.add(obScroll);

        // Liquidity & Info
        JPanel infoPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        infoPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Market Liquidity & Info", 0, 0, new Font("Arial", Font.BOLD, 22)));

        infoPanel.add(liquidityLabel);
        infoPanel.add(sentimentLabel);
        infoPanel.add(spreadLabel);
        infoPanel.add(priceLabel);
        contentPanel.add(infoPanel);

        // Volume at Price
        JTable vapTable = new JTable(volumePriceModel);
        vapTable.setFont(new Font("Arial", Font.PLAIN, 20));
        vapTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 22));
        vapTable.setRowHeight(40);
        JScrollPane vapScroll = new JScrollPane(vapTable);
        vapScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Volume by Price Level", 0, 0, new Font("Arial", Font.BOLD, 22)));
        contentPanel.add(vapScroll);

        add(contentPanel, BorderLayout.CENTER);

        deepDiveLabel.setFont(new Font("Arial", Font.ITALIC, 32));
        add(deepDiveLabel, BorderLayout.SOUTH);
    }

    private void loadRealData(String title, String slug) {
        try {
            JsonObject market = Http.getJsonObject("https://gamma-api.polymarket.com/markets/slug/" + slug);

            Json tokenData = market.get("clobTokenIds");
            JsonArray tokens = null;

            if (tokenData instanceof JsonArray ja) {
                tokens = ja;
            } else if (tokenData instanceof JsonString js) {
                // If it's a string, try to parse it as a JSON array
                Json parsed = Json.parse(js.inner());
                if (parsed instanceof JsonArray ja)
                    tokens = ja;
            }

            if (tokens == null || tokens.elements().isEmpty()) {
                SwingUtilities.invokeLater(() -> deepDiveLabel.setText("No tokens found for: " + title));
                return;
            }

            String yesTokenId = ((JsonString) tokens.elements().get(0)).inner();
            JsonObject book = null;
            try {
                book = Http.getJsonObject("https://clob.polymarket.com/book?token_id=" + yesTokenId);
            } catch (Exception e) {
                loadingStatusLabel.setText("Error fetching market");
                throw e;
            }

            final JsonObject finalBook = book;
            SwingUtilities.invokeLater(() -> {
                orderBookModel.setRowCount(0);

                List<Quote> askList = getBestAsks(finalBook);
                List<Quote> bidList = getBestBids(finalBook);

                double bestAsk = -1, bestBid = -1;

                if (askList.isEmpty() && bidList.isEmpty()) {
                    deepDiveLabel.setText("Deep Dive: " + title + " (No Active Order Book)");
                } else {
                    deepDiveLabel.setText("Deep Dive: " + title);
                }

                // Add top 5 asks (will be the lowest prices at the bottom of the ask section)
                for (int i = 0; i < Math.min(5, askList.size()); i++) {
                    Quote quote = askList.get(i);
                    if (i == 0)
                        bestAsk = quote.price();
                    orderBookModel.addRow(new Object[] { quote.price(), quote.volume(), "ASK" });
                }

                // Add top 5 bids (highest prices at the top of the bid section)
                for (int i = 0; i < Math.min(5, bidList.size()); i++) {
                    Quote quote = bidList.get(i);
                    if (i == 0)
                        bestBid = quote.price();
                    orderBookModel.addRow(new Object[] { quote.price(), quote.volume(), "BID" });
                }

                liquidityLabel.setText("Liquidity: " + formatVal(market.getString("liquidity")));

                Component[] comps = ((JPanel) ((JPanel) getComponent(1)).getComponent(1)).getComponents();
                if (bestAsk != -1 && bestBid != -1) {
                    double spreadCents = (bestAsk - bestBid) * 100;
                    spreadLabel.setText(String.format("Spread: %.3fc", spreadCents));
                } else {
                    spreadLabel.setText("Spread: ?");
                }

                setSentimentLabel(finalBook);

                priceLabel.setText("Last Price: " + formatCents(market.getDouble("lastTradePrice")));
                volumePriceModel.setRowCount(0);
                volumePriceModel.addRow(new Object[] { "Total", formatVal(market.getString("volume")) });
            });

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> deepDiveLabel.setText("Error loading data: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    record Quote(double price, double volume) {
    }

    private List<Quote> getBestBids(JsonObject finalBook) {
        List<Quote> bidList = new ArrayList<>();
        if (finalBook != null && finalBook.get("bids") instanceof JsonArray bids) {
            for (Json e : bids.elements()) {
                JsonObject bid = (JsonObject) e;
                bidList.add(new Quote(bid.getDouble("price"), bid.getDouble("size")));
            }
        }
        bidList.sort((b1, b2) -> -Double.compare(b1.price(), b2.price()));
        return bidList;
    }

    private List<Quote> getBestAsks(JsonObject finalBook) {
        List<Quote> askList = new ArrayList<>();
        if (finalBook != null && finalBook.get("asks") instanceof JsonArray asks) {
            for (Json e : asks.elements()) {
                JsonObject ask = (JsonObject) e;
                askList.add(new Quote(ask.getDouble("price"), ask.getDouble("size")));
            }
        }
        askList.sort(Comparator.comparingDouble(Quote::price));
        return askList;
    }

    private void setSentimentLabel(JsonObject finalBook) {
        List<Quote> bids = getBestBids(finalBook);
        List<Quote> asks = getBestAsks(finalBook);
        double goodBidVolume = bids.stream()
                .limit(5)
                .mapToDouble(Quote::volume)
                .sum();

        double goodAskVolume = asks.stream()
                .limit(5)
                .mapToDouble(Quote::volume)
                .sum();

        double bestBid = bids.isEmpty() ? 0 : bids.getFirst().price();
        double bestAsk = asks.isEmpty() ? 1 : asks.getFirst().price();

        double mid = (bestBid + bestAsk) / 2.0;

        double totalVolume = goodBidVolume + goodAskVolume;
        double pressure = totalVolume > 0 ? (goodBidVolume - goodAskVolume) / totalVolume : 0;

        String sentiment;
        Color colour;
        if (mid < 0.15) {
            sentiment = "Everyone for themselves, GTFO";
            colour = Color.RED;
        } else if (mid < 0.35) {
            sentiment = "unlikely";
            colour = new Color(204, 204, 0);

        } else if (mid < 0.65) {
            sentiment = "Pretty even";
            colour = new Color(156, 156, 156);
            if (pressure > 0.3) {
                sentiment += " maybe YES";
                colour = new Color(89, 180, 0);
            } else if (pressure < -0.3) {
                sentiment += " maybe NO";
                colour = new Color(183, 117, 0);
            }
        } else if (mid < 0.85) {
            sentiment = "quite likely";
            colour = new Color(90, 180, 0);
        } else {
            sentiment = "more hyped than beyond meat";
            colour = new Color(0, 128, 255);
            if (pressure < -0.3) {
                sentiment += "starting to sell";
            }
        }

        sentimentLabel.setText("Sentiment: " + sentiment);
        sentimentLabel.setForeground(colour);

    }

    private String formatCents(double dollars) {
        double cents = dollars * 100d;
        return String.format("%.3fc", cents);
    }

    private String formatVal(String val) {
        if (val == null || val.equals("null"))
            return "0.00";
        try {
            double d = Double.parseDouble(val);
            return String.format("$%,.2f", d);
        } catch (Exception e) {
            return val;
        }
    }

    public void updateMarketSelector(Map<String, String> markets) {
        String currentSelection = (String) marketSelector.getSelectedItem();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) marketSelector.getModel();
        boolean changed = false;
        for (String title : markets.keySet()) {
            titleToSlug.put(title, markets.get(title));
            if (model.getIndexOf(title) == -1) {
                model.addElement(title);
                changed = true;
            }
        }
        if (changed && currentSelection != null) {
            marketSelector.setSelectedItem(currentSelection);
        }
    }
}
