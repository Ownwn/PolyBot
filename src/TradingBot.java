// Note: This class depends on Http.java and Json.java from the 'src' directory.
// Ensure your build path includes the 'src' directory.

import java.util.HashMap;
import java.util.Map;

public class TradingBot {
    private static final String TS_SERVER_URL = "http://localhost:3000";

    public interface LogListener {
        void onLog(String message);
    }

    private LogListener logListener;

    private boolean simulationMode = true;
    private double simulatedPnL = 0.0;

    private Map<String, Double> simPositions = new HashMap<>(); // size
    private Map<String, Double> simEntryPrices = new HashMap<>(); // entry price

    public Map<String, Double> getSimPositions() {
        return simPositions;
    }

    public Map<String, Double> getSimEntryPrices() {
        return simEntryPrices;
    }

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    public void setLogListener(LogListener listener) {
        this.logListener = listener;
    }

    public void log(String message) {
        System.out.println(message);
        if (logListener != null) {
            logListener.onLog(message);
        }
    }

    public double getSimulatedPnL() {
        return simulatedPnL;
    }

    public JsonObject placeOrder(String tokenId, String side, double price, double size) {
        if (simulationMode) {
            log("\norder placed: " + side.toLowerCase() + " " + size + " of token " + tokenId
                    + " @ $" + price);

            double currentSize = simPositions.getOrDefault(tokenId, 0.0);
            double currentEntry = simEntryPrices.getOrDefault(tokenId, 0.0);

            if (side.equalsIgnoreCase("BUY")) {
                double totalCost = (currentSize * currentEntry) + (size * price);
                double newSize = currentSize + size;
                simPositions.put(tokenId, newSize);
                simEntryPrices.put(tokenId, totalCost / newSize);
                log(String.format("pos updated: %.2f shares @ avg $%.3f\n\n", newSize,
                        (totalCost / newSize)));
            } else if (side.equalsIgnoreCase("SELL")) {
                double sellSize = Math.min(currentSize, size);
                if (sellSize > 0) {
                    double pnl = (price - currentEntry) * sellSize;
                    simulatedPnL += pnl;
                    double newSize = currentSize - sellSize;
                    simPositions.put(tokenId, newSize);
                    if (newSize == 0) {
                        simEntryPrices.remove(tokenId);
                    }
                    log(String.format(
                            "trade closed. realized pnl: $%+.2f | total sim pnl: $%+.2f\n\n", pnl,
                            simulatedPnL));
                } else {
                    log("ignored sell - nothing to sell.\n");
                }
            }

            return (JsonObject) Json.parse("{\"status\":\"simulated_success\"}");
        }

        String path = "/order";

        String body = "{" +
                "\"tokenID\":\"" + tokenId + "\"," +
                "\"side\":\"" + side.toUpperCase() + "\"," +
                "\"price\":" + price + "," +
                "\"size\":" + size +
                "}";

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        try {
            return Http.post(TS_SERVER_URL + path, body, headers);
        } catch (Exception e) {
            System.err.println("Error placing order: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public JsonObject getOrderBook(String tokenId) {
        String path = "/book?token_id=" + tokenId;
        try {
            return Http.getJsonObject(TS_SERVER_URL + path);
        } catch (Exception e) {
            System.err.println("Error fetching order book: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
