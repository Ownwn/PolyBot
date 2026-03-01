import { load } from "https://deno.land/std@0.204.0/dotenv/mod.ts";
await load({ export: true, envPath: "./ts_server/.env" });

import { ClobClient } from "npm:@polymarket/clob-client@^5.2.3";

async function debug() {
    try {
        const client = new ClobClient("https://clob.polymarket.com", 137);
        const marketId = "0x4b02efe53e631ada84681303fd66d79ad615f3d2b6a28b4633d43d935f89af58";
        console.log("Fetching market info for:", marketId);
        const market = await client.getMarket(marketId);
        console.log("Market keys:", Object.keys(market));
        console.log("Tokens:", JSON.stringify(market.tokens, null, 2));

        if (market.tokens) {
            for (const token of market.tokens) {
                 try {
                     const price = await client.getMidpoint(token.token_id);
                     console.log(`Token ${token.outcome} (${token.token_id}) price:`, price.mid);
                 } catch (e) {
                     console.log(`Token ${token.outcome} price fetch failed:`, e.message);
                 }
            }
        }
    } catch (e) {
        console.error("Debug failed:", e);
    }
}

debug();
