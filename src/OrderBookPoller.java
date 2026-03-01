public class OrderBookPoller {
    private final TradingBot bot;
    private final String tokenId;
    private final QuantStrategy strategy;
    private final int pollIntervalMs;
    private volatile boolean running = false;

    public OrderBookPoller(TradingBot bot, String tokenId, QuantStrategy strategy, int pollIntervalMs) {
        this.bot = bot;
        this.tokenId = tokenId;
        this.strategy = strategy;
        this.pollIntervalMs = pollIntervalMs;
    }

    public void start() {
        if (running)
            return;
        running = true;
        System.out.println("starting poller for token: " + tokenId);

        new Thread(() -> {
            while (running) {
                try {
                    long start = System.currentTimeMillis();
                    JsonObject book = bot.getOrderBook(tokenId);

                    if (book != null) {
                        strategy.onOrderBookUpdate(book);
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    long sleepTime = pollIntervalMs - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                } catch (Exception e) {
                    System.err.println("Poller error: " + e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
    }
}
