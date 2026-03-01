import { load } from "https://deno.land/std@0.204.0/dotenv/mod.ts";
await load({ export: true, envPath: ".env" });
import express from "npm:express@4.18.2";
import { ClobClient } from "npm:@polymarket/clob-client@^5.2.3";
import { Wallet } from "ethers";


const app = express();
app.use(express.json());

const PORT = 3000;

const credentials = {
    key: process.env.POLY_API_KEY,
    secret: process.env.POLY_API_SECRET,
    passphrase: process.env.POLY_API_PASSPHRASE
};

if (!credentials.key || !credentials.secret || !credentials.passphrase) {
    throw Error("One of credentials is not set")
}

const privateKey = process.env.PRIVATE_KEY
if (!privateKey) {
    throw Error("PRIVATE_KEY environment variable is not set")
}

const proxyAddress = process.env.POLY_PROXY_ADDRESS
if (!proxyAddress) {
    throw Error("missing proxy address (public)")
}

const wallet = new Wallet(privateKey)

const client = new ClobClient(
    "https://clob.polymarket.com",
    137, wallet,
    credentials,
    1,
    proxyAddress
);

const marketCache = new Map<string, string>();
const priceCache = new Map<string, { price: number, timestamp: number }>();

async function getCurrentPrice(tokenId: string, market: any): Promise<number> {
    const now = Date.now();
    const cached = priceCache.get(tokenId);
    if (cached && (now - cached.timestamp < 10000)) { // 10s cache
        return cached.price;
    }

    if (market && Array.isArray(market.tokens)) {
        const token = market.tokens.find((t: any) => t.token_id === tokenId);
        if (token) {
            if (token.winner === true) return 1.0;
            if (token.winner === false && market.closed) return 0.0;

            if (typeof token.price === 'number') {
                return token.price;
            }
        }
    }

    try {
        const price = await client.getMidpoint(tokenId);
        const p = parseFloat(price.mid);
        priceCache.set(tokenId, { price: p, timestamp: now });
        return p;
    } catch (error) {

        if (market && Array.isArray(market.tokens)) {
            const token = market.tokens.find((t: any) => t.token_id === tokenId);
            if (token && typeof token.price === 'number') {
                return token.price;
            }
        }
        return 0;
    }
}

const marketFullCache = new Map<string, any>();

async function getMarketFull(marketId: string) {
    if (!marketId) return null;
    if (marketFullCache.has(marketId)) {
        return marketFullCache.get(marketId);
    }
    try {
        const market = await client.getMarket(marketId);
        marketFullCache.set(marketId, market);
        return market;
    } catch (error) {
        console.error(`Error fetching full market ${marketId}:`, error);
        return null;
    }
}

async function getMarketTitle(marketId: string) {
    const market = await getMarketFull(marketId);
    return market ? market.question : "Unknown Market";
}

app.get("/book", async (req: any, res: any) => {
    try {
        const tokenId = req.query.token_id as string;
        if (!tokenId) {
            return res.status(400).json({ error: "token_id query parameter is required" });
        }
        const resp = await fetch("https://clob.polymarket.com/book?token_id=" + tokenId);
        if (!resp.ok) {
            return res.status(resp.status).json({ error: "CLOB API error: " + resp.statusText });
        }
        const book = await resp.json();
        res.json(book);
    } catch (error: any) {
        console.error("Error fetching order book:", error);
        res.status(500).json({ error: error.message });
    }
});

app.get("/trades", async (_req: any, res: any) => {
    try {
        const marketId = "0x4b02efe53e631ada84681303fd66d79ad615f3d2b6a28b4633d43d935f89af58"; // todo
        const trades: any = await client.getTrades({
            market: marketId,
        },
            true);

        const market = await getMarketFull(marketId);
        const marketName = market ? market.question : "Unknown Market";

        const enhancedTrades = await Promise.all(
            (Array.isArray(trades) ? trades : []).map(async (t: any) => {
                const currentPrice = await getCurrentPrice(t.asset_id, market);
                return {
                    ...t,
                    market_name: marketName,
                    current_price: currentPrice
                };
            })
        );

        res.json(enhancedTrades);
    } catch (error: any) {
        console.error("Error fetching trades:", error);
        res.status(500).json({ error: error.message });
    }
});

app.post("/order", async (req: any, res: any) => {
    try {
        const { tokenID, side, price, size } = req.body;

        if (!tokenID || !side || !price || !size) {
            return res.status(400).json({ error: "tokenID, side, price, and size are required" });
        }

        const createdOrder = await client.createOrder({
            token_id: tokenID,
            side: side.toLowerCase(),
            price: String(price),
            size: String(size),
        });

        res.json(createdOrder);
    } catch (error: any) {
        console.error("Error placing order:", error);
        res.status(500).json({ error: error.message });
    }
});


app.delete("/orders", async (_req: any, res: any) => {
    try {
        const result = await client.cancelAll();
        res.json(result);
    } catch (error: any) {
        console.error("Error cancelling orders:", error);
        res.status(500).json({ error: error.message });
    }
});

app.listen(PORT, () => {
    console.log(`Polymarket Bridge Server (Deno) listening on port ${PORT}`);
});