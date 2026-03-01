public class TestParsing {
    public static void main(String[] args) throws Exception {
        String input = "btc-updown-15m-1772345700";
        JsonObject market = Http.getJsonObject("https://gamma-api.polymarket.com/markets/slug/" + input);
        Json tokenData = market.get("clobTokenIds");
        JsonArray tokens = null;
        if (tokenData instanceof JsonArray ja) {
            tokens = ja;
            System.out.println("It was an array");
        } else if (tokenData instanceof JsonString(String inner)) {
            System.out.println("It was a string: " + inner);
            Json parsed = Json.parse(inner);
            if (parsed instanceof JsonArray ja) {
                tokens = ja;
                System.out.println("Parsed to array successfully");
            } else {
                System.out.println("Parsed but not array: " + parsed.getClass().getName());
            }
        } else {
            System.out.println("Unknown type for tokenData: " + (tokenData == null ? "null" : tokenData.getClass().getName()));
        }

        if (tokens == null || tokens.elements().isEmpty()) {
            System.out.println("Could not resolve Token ID for slug: " + input);
            return;
        }
        String token = ((JsonString) tokens.elements().get(0)).inner();
        System.out.println("Resolved slug '" + input + "' to token: " + token);
    }
}
