import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScraperUI extends JFrame {
    private final MessageHandler messageHandler;
    private final LeaderboardTracker leaderboard = new LeaderboardTracker();
    private final DashboardPanel dashboard;
    private final DeepDivePanel deepDive;
    private final TradingPanel tradingPanel;
    private final JTabbedPane tabbedPane = new JTabbedPane();

    private boolean paused = false;
    private String smartPeriod = "WEEK";

    public ScraperUI(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        setTitle("Polymarket Live Trade Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        dashboard = new DashboardPanel(this::handleUserTradeAction, JLink::openWebpage);
        deepDive = new DeepDivePanel();
        tradingPanel = new TradingPanel();

        tabbedPane.setFont(new Font("Arial", Font.BOLD, 30));
        tabbedPane.addTab("Dashboard", dashboard);
        tabbedPane.addTab("Market Deep Dive", deepDive);
        tabbedPane.addTab("Trading", tradingPanel);
        add(tabbedPane, BorderLayout.CENTER);

        setSize(1800, 1200);
        setLocationRelativeTo(null);

        setupTimers();
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton pauseBtn = new JButton("Pause Feed");
        pauseBtn.setFont(new Font("Arial", Font.BOLD, 32));
        pauseBtn.addActionListener(e -> {
            paused = !paused;
            pauseBtn.setText(paused ? "Resume Feed" : "Pause Feed");
            pauseBtn.setBackground(paused ? Color.RED : null);
        });

        JButton loadLogBtn = new JButton("Load Raw Log");
        loadLogBtn.setFont(new Font("Arial", Font.BOLD, 32));
        loadLogBtn.addActionListener(e -> loadLog());

        JButton filterBtn = new JButton("Filters");
        filterBtn.setFont(new Font("Arial", Font.BOLD, 32));
        filterBtn.addActionListener(e -> showFilters());

        JButton clearBtn = new JButton("Clear Stats");
        clearBtn.setFont(new Font("Arial", Font.BOLD, 32));
        clearBtn.addActionListener(e -> {
            messageHandler.getStats().clear();
            refresh();
            addLog("--- Stats Cleared ---");
        });

        topPanel.add(pauseBtn);
        topPanel.add(loadLogBtn);
        topPanel.add(filterBtn);
        topPanel.add(clearBtn);

        JComboBox<String> periodBox = new JComboBox<>(new String[] { "WEEK", "MONTH" });
        periodBox.setFont(new Font("Arial", Font.BOLD, 32));
        periodBox.addActionListener(e -> {
            smartPeriod = (String) periodBox.getSelectedItem();
            new Thread(() -> leaderboard.refresh(smartPeriod)).start();
        });
        topPanel.add(new JLabel("Smart Period:") {
            {
                setFont(new Font("Arial", Font.BOLD, 32));
            }
        });
        topPanel.add(periodBox);

        return topPanel;
    }

    private void setupTimers() {
        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> refresh());
        timer.start();
        javax.swing.Timer leaderboardTimer = new javax.swing.Timer(300000,
                e -> new Thread(() -> leaderboard.refresh(smartPeriod)).start());
        leaderboardTimer.start();
        new Thread(() -> leaderboard.refresh(smartPeriod)).start();
    }

    private void handleUserTradeAction(String user, String context) {
        String address = context.equals("click") ? messageHandler.getStats().getFullAddress(user) : context;
        showUserTrades(user, address);
    }

    private void refresh() {
        if (paused)
            return;
        TransactionStats stats = messageHandler.getStats();
        java.util.function.Predicate<Transaction> filter = t -> !messageHandler.isExcluded(t);

        var buys = stats.getTopBoughtStats(filter).stream()
                .map(e -> Map.entry(e.getKey(),
                        new Object[] { e.getValue().title(), String.format("%.2f", e.getValue().value()), e.getKey() }))
                .toList();
        var sells = stats.getTopSoldStats(filter).stream()
                .map(e -> Map.entry(e.getKey(),
                        new Object[] { e.getValue().title(), String.format("%.2f", e.getValue().value()), e.getKey() }))
                .toList();
        var spenders = stats.getTopSpenders(filter);
        var smarts = leaderboard.getSmartTraders().stream()
                .map(s -> new Object[] { s.userName(), String.format("%.2f", s.pnl()), String.format("%.2f", s.vol()),
                        s.proxyWallet() })
                .toList();

        dashboard.updateData(buys, sells, spenders, smarts);

        Map<String, String> markets = new HashMap<>();
        for (var e : buys)
            markets.put((String) e.getValue()[0], e.getKey());
        for (var e : sells)
            markets.put((String) e.getValue()[0], e.getKey());
        deepDive.updateMarketSelector(markets);
    }

    private void showUserTrades(String user, String address) {
        new Thread(() -> {
            List<Transaction> trades = messageHandler.getStats().getRecentTradesForUser(user,
                    t -> !messageHandler.isExcluded(t));
            if (trades.isEmpty() && address != null) {
                addLog("--- Fetching recent trades for " + user + " from API ---");
                trades = LeaderboardTracker.fetchRecentTrades(address);
            }
            final List<Transaction> finalTrades = trades;
            SwingUtilities.invokeLater(() -> {
                if (finalTrades.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No recent trades found for " + user);
                    return;
                }
                UserTradeDialog.show(this, user, finalTrades);
            });
        }).start();
    }

    private void showFilters() {
        String current = messageHandler.getExcludedPatternsString();
        String input = JOptionPane.showInputDialog(this, "Enter patterns to exclude:", current);
        if (input != null) {
            messageHandler.setExcludedPatterns(input);
            refresh();
            addLog("--- Filters Updated: " + messageHandler.getExcludedPatternsString() + " ---");
        }
    }

    private void loadLog() {
        String date = JOptionPane.showInputDialog(this, "Enter date (yyyy-MM-dd):",
                java.time.LocalDate.now().toString());
        if (date != null && !date.isBlank()) {
            messageHandler.getStats().clear();
            new Thread(() -> {
                try {
                    List<String> lines = DataLogger.readRawLog(date);
                    addLog("--- Replaying log from " + date + " ---");
                    for (String line : lines)
                        messageHandler.handle(line, false);
                    addLog("--- Finished replaying log ---");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
                }
            }).start();
        }
    }

    public void addLog(String message) {
        if (paused && !message.startsWith("---"))
            return;
        dashboard.addLog(message);
    }
}