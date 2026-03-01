public class TradingBotMain {
    public static void main(String[] args) {
        System.out.println("Initializing Quantitative Trading Bot (Simulated Mode)");

        TradingBot bot = new TradingBot();

        // example
        String btc100kTokenId = "111128191581505463501777127559667396812474366956707382672202929745167742497287";
        double buyThreshold = 0.30;
        double sellThreshold = 0.0;
        double tradeSize = 10.0;

        QuantStrategy strategy = new QuantStrategy(bot, btc100kTokenId, buyThreshold, sellThreshold, tradeSize);

        OrderBookPoller poller = new OrderBookPoller(bot, btc100kTokenId, strategy, 1000); // 1-second polling

        poller.start();

        System.out.println("Bot is running. Press CTRL+C to stop.");
    }
}
