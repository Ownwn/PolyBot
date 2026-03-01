public class QuantStrategy {
    private final TradingBot bot;
    private final String tokenId;
    private final double imbalanceThresholdBuy;
    private final double imbalanceThresholdSell;
    private final double orderSize;

    private double currentPosition = 0;

    public QuantStrategy(TradingBot bot, String tokenId, double thresholdBuy, double thresholdSell, double orderSize) {
        this.bot = bot;
        this.tokenId = tokenId;
        this.imbalanceThresholdBuy = thresholdBuy;
        this.imbalanceThresholdSell = thresholdSell;
        this.orderSize = orderSize;
    }

    private double maxPosition = 50.0;
    private double stopLossPercentage = 0.15; // 15% stop loss
    private double maxSpread = 0.05; // 5 cent spread limit
    private double takeProfitPercentage = 0.05; // 5% take profit
    private double currentEntryPrice = 0.0;

    public void onOrderBookUpdate(JsonObject bookData) {
        if (bookData == null || bookData.get("bids") == null || bookData.get("asks") == null) {
            return;
        }

        double vBid = calculateVolume(bookData.get("bids"), 5);
        double vAsk = calculateVolume(bookData.get("asks"), 5);

        if (vBid + vAsk == 0)
            return;

        double imbalanceRatio = (vBid - vAsk) / (vBid + vAsk);

        double bestBid = getBestPrice(bookData.get("bids"));
        double bestAsk = getBestPrice(bookData.get("asks"));

        bot.log(String.format(
                "ir: %+.3f | vbid: $%.0f | vask: $%.0f | pos: %.2f (avg $%.3f) | bid: $%.3f | ask: $%.3f\n",
                imbalanceRatio, vBid, vAsk, currentPosition, currentEntryPrice, bestBid, bestAsk));

        if (currentPosition > 0) {
            if (bestBid > 0 && bestBid < currentEntryPrice * (1 - stopLossPercentage)) {
                bot.log("stop loss hit @ " + bestBid);
                bot.placeOrder(tokenId, "SELL", bestBid, currentPosition);
                currentPosition = 0;
                currentEntryPrice = 0;
                return; // dont process other signals
            }
            if (bestBid > 0 && bestBid > currentEntryPrice * (1 + takeProfitPercentage)) {
                bot.log("take profit triggered @ " + bestBid + " <<<");
                bot.placeOrder(tokenId, "SELL", bestBid, currentPosition);
                currentPosition = 0;
                currentEntryPrice = 0;
                return; // dont process other signals
            }
        }

        if (imbalanceRatio > imbalanceThresholdBuy && currentPosition < maxPosition) {
            if (bestAsk - bestBid > maxSpread) {
                bot.log("skipping buy cuz spread too wide ($" + String.format("%.3f", bestAsk - bestBid)
                        + ")");
                return;
            }
            bot.log("buying @ " + bestAsk);
            bot.placeOrder(tokenId, "BUY", bestAsk, orderSize);
            double totalCost = (currentPosition * currentEntryPrice) + (orderSize * bestAsk);
            currentPosition += orderSize;
            currentEntryPrice = totalCost / currentPosition;
        } else if (imbalanceRatio < imbalanceThresholdSell && currentPosition > 0) {
            bot.log("selling @ " + bestBid);
            bot.placeOrder(tokenId, "SELL", bestBid, currentPosition);
            currentPosition = 0;
            currentEntryPrice = 0;
        }
    }

    private double calculateVolume(Json levelsJson, int depth) {
        if (!(levelsJson instanceof JsonArray arr))
            return 0;
        double vol = 0;
        int count = 0;
        for (int i = arr.elements().size() - 1; i >= 0 && count < depth; i--) {
            JsonObject level = (JsonObject) arr.elements().get(i);
            try {
                vol += Double.parseDouble(level.getString("size"));
                count++;
            } catch (Exception ignored) {
            }
        }
        return vol;
    }

    private double getBestPrice(Json levelsJson) {
        if (levelsJson instanceof JsonArray arr && !arr.elements().isEmpty()) {
            JsonObject level = (JsonObject) arr.elements().get(arr.elements().size() - 1);
            try {
                return Double.parseDouble(level.getString("price"));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
