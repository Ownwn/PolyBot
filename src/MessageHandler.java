import java.util.*;

public class MessageHandler {
    private final List<String> excludedPatterns = new ArrayList<>(
            List.of("-5m-", "-15m-", "Bitcoin", "Ethereum", "Solana", "XRP"));
    private final DataLogger logger = new DataLogger();
    private final TransactionStats stats = new TransactionStats();
    private ScraperUI ui;

    private static final java.util.regex.Pattern DATE_PATTERN = java.util.regex.Pattern
            .compile("20[2-9][0-9]-[0-1][0-9]-[0-3][0-9]");
    private String todayStrCache = java.time.LocalDate.now().toString();
    private long lastDateUpdate = System.currentTimeMillis();

    public void setUI(ScraperUI ui) {
        this.ui = ui;
    }

    public TransactionStats getStats() {
        return stats;
    }

    public void setExcludedPatterns(String patterns) {
        excludedPatterns.clear();
        for (String p : patterns.split(",")) {
            if (!p.trim().isEmpty())
                excludedPatterns.add(p.trim());
        }
    }

    public String getExcludedPatternsString() {
        return String.join(", ", excludedPatterns);
    }

    private static boolean containsIgnoreCase(String src, String what) {
        final int length = what.length();
        if (length == 0)
            return true;
        for (int i = src.length() - length; i >= 0; i--) {
            if (src.regionMatches(true, i, what, 0, length))
                return true;
        }
        return false;
    }

    public boolean isExcluded(Transaction t) {
        String slug = t.slug();
        String title = t.title();
        for (String p : excludedPatterns) {
            if (slug.contains(p) || title.contains(p))
                return true;
        }
        // Past Date Filter (for sports/events)
        java.util.regex.Matcher m = DATE_PATTERN.matcher(slug);
        if (m.find()) {
            String dateStr = m.group();
            long now = System.currentTimeMillis();
            if (now - lastDateUpdate > 3600_000) { // update cached date every hour
                todayStrCache = java.time.LocalDate.now().toString();
                lastDateUpdate = now;
            }
            if (dateStr.compareTo(todayStrCache) < 0)
                return true;
        }
        // Keywords for finished markets
        if (containsIgnoreCase(title, "completed") || containsIgnoreCase(title, "resolved") ||
                containsIgnoreCase(slug, "completed") || containsIgnoreCase(slug, "resolved")) {
            return true;
        }
        return false;
    }

    public void handle(CharSequence charSequence) {
        handle(charSequence, true);
    }

    public void handle(CharSequence charSequence, boolean shouldLog) {
        String raw = charSequence.toString().trim();
        if (raw.isBlank())
            return;
        if (shouldLog)
            logger.logRaw(raw);

        try {
            Json parsed = Json.parse(raw);
            if (!(parsed instanceof JsonObject root))
                return;
            JsonObject payload = root.getObj("payload");
            if (payload == null)
                return;

            double price = payload.getDouble("price");
            if (price >= 0.99 || price <= 0.01)
                return;

            String title = payload.getString("title");
            double size = payload.getDouble("size");
            double value = price * size;
            if (value < 5)
                return;

            String user = "Unknown";
            String rawAddress = null;
            Json wallet = payload.get("proxyWallet");
            if (wallet instanceof JsonString ws)
                rawAddress = ws.inner();

            Json name = payload.get("name");
            if (name instanceof JsonString js && !js.inner().isBlank()) {
                user = js.inner();
            } else if (rawAddress != null) {
                user = rawAddress;
                if (user.length() > 10)
                    user = user.substring(0, 6) + "..." + user.substring(user.length() - 4);
            }

            if (rawAddress != null)
                stats.trackAddress(user, rawAddress);

            long ts = payload.getLong("timestamp") * 1000;
            if (System.currentTimeMillis() - ts > 300 * 1000)
                return;

            Transaction transaction = new Transaction(title, user, payload.getString("slug"), payload.getString("side"),
                    ts, value);
            if (shouldLog)
                logger.logTransaction(transaction);

            stats.add(transaction);

            if (ui != null && !isExcluded(transaction)) {
                ui.addLog(transaction.pretty());
            }
        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }
}