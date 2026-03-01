import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public record MessageListener(String wssUrl) {
    private static final String tradeSubscription = "{\"action\":\"subscribe\",\"subscriptions\":[{\"topic\":\"activity\",\"type\":\"trades\"}]}";

    public void startListening(Consumer<CharSequence> listener) {
        URI uri = URI.create(wssUrl);

        CompletableFuture<Void> infiniteComplete = new CompletableFuture<>();

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://polymarket.com")
                .buildAsync(uri, new WebSocket.Listener() {
                    StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("WebSocket Connected to " + wssUrl);
                        webSocket.request(1);
                        webSocket.sendText(tradeSubscription, true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                            CharSequence data,
                            boolean last) {
                        buffer.append(data);
                        if (last) {
                            try {
                                listener.accept(buffer.toString());
                            } catch (Exception e) {
                                System.err.println("Error handling message: " + e.getMessage());
                            }
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                            int statusCode,
                            String reason) {
                        System.out.println("WebSocket Closed: " + statusCode + " " + reason);
                        infiniteComplete.complete(null);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("WebSocket Error: " + error.getMessage());
                        infiniteComplete.completeExceptionally(error);
                    }
                }).join();

        try {
            infiniteComplete.get();
        } catch (Exception e) {
            System.err.println("Listener finished with error: " + e.getMessage());
        }
    }
}