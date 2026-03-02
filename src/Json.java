import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class Parser {
    private final CharSequence s;
    private int i;
    private final int len;

    static Json parseJson(CharSequence raw) {
        Parser p = new Parser(raw);
        return p.parse();
    }

    private Parser(CharSequence raw) {
        this.s = raw;
        this.len = raw.length();
        this.i = 0;
        skipWhitespace();
    }

    private void skipWhitespace() {
        while (i < len) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (i >= len)
            throw new RuntimeException("Unexpected end of input");
        return s.charAt(i);
    }

    private char next() {
        char c = peek();
        i++;
        skipWhitespace();
        return c;
    }

    private char nextRaw() {
        if (i >= len)
            throw new RuntimeException("Unexpected end of input");
        return s.charAt(i++);
    }

    public void expectChar(char expected) {
        char c = next();
        if (c != expected)
            throw new RuntimeException("needed: " + expected + " got: " + c + " at " + i);
    }

    private Json parse() {
        if (i >= len)
            throw new RuntimeException("Unexpected end of input");
        char c = peek();
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
            default -> throw new IllegalStateException("Unexpected value: " + c + " at " + i);
        };
    }

    private JsonString parseString() {
        i++; // skip opening "
        StringBuilder res = new StringBuilder();
        while (i < len) {
            char c = nextRaw();
            if (c == '"') {
                skipWhitespace();
                return new JsonString(res.toString());
            } else if (c == '\\') {
                char escaped = nextRaw();
                switch (escaped) {
                    case '"' -> res.append('"');
                    case '\\' -> res.append('\\');
                    case '/' -> res.append('/');
                    case 'b' -> res.append('\b');
                    case 'f' -> res.append('\f');
                    case 'n' -> res.append('\n');
                    case 'r' -> res.append('\r');
                    case 't' -> res.append('\t');
                    case 'u' -> {
                        if (i + 4 > len)
                            throw new RuntimeException("Invalid unicode escape");
                        String hex = s.subSequence(i, i + 4).toString();
                        res.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> res.append(escaped);
                }
            } else {
                res.append(c);
            }
        }
        throw new RuntimeException("Missing closing JSON string");
    }

    private JsonBoolean parseBoolean() {
        if (i + 4 <= len && s.subSequence(i, i + 4).equals("true")) {
            i += 4;
            skipWhitespace();
            return new JsonBoolean(true);
        } else if (i + 5 <= len && s.subSequence(i, i + 5).equals("false")) {
            i += 5;
            skipWhitespace();
            return new JsonBoolean(false);
        }
        throw new RuntimeException("expecting bool at " + i);
    }

    private JsonNull parseNull() {
        if (i + 4 <= len && s.subSequence(i, i + 4).equals("null")) {
            i += 4;
            skipWhitespace();
            return new JsonNull();
        }
        throw new RuntimeException("expecting null at " + i);
    }

    private Json parseNumber() {
        int start = i;
        if (s.charAt(i) == '-')
            i++;
        while (i < len && Character.isDigit(s.charAt(i)))
            i++;

        boolean isDouble = false;
        if (i < len && s.charAt(i) == '.') {
            isDouble = true;
            i++;
            while (i < len && Character.isDigit(s.charAt(i)))
                i++;
        }
        if (i < len && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            isDouble = true;
            i++;
            if (i < len && (s.charAt(i) == '+' || s.charAt(i) == '-'))
                i++;
            while (i < len && Character.isDigit(s.charAt(i)))
                i++;
        }

        String numStr = s.subSequence(start, i).toString();
        skipWhitespace();
        if (isDouble)
            return new JsonDouble(Double.parseDouble(numStr));
        else
            return new JsonLong(Long.parseLong(numStr));
    }

    private JsonObject parseObject() {
        expectChar('{');
        List<JsonPair> pairs = new ArrayList<>();

        if (peek() == '}') {
            next();
            return new JsonObject(pairs);
        }

        while (true) {
            pairs.add(parsePair());
            char c = peek();
            if (c == '}') {
                next();
                break;
            }
            if (c != ',')
                throw new RuntimeException("Expected ',' or '}' in object, got " + c + " at " + i);
            next();
        }
        return new JsonObject(pairs);
    }

    private JsonPair parsePair() {
        if (peek() != '"')
            throw new RuntimeException("Expected string key in object at " + i);
        JsonString key = parseString();
        expectChar(':');
        Json val = parse();
        return new JsonPair(key, val);
    }

    private JsonArray parseArray() {
        expectChar('[');
        List<Json> elems = new ArrayList<>();

        if (peek() == ']') {
            next();
            return new JsonArray(elems);
        }

        while (true) {
            elems.add(parse());
            char c = peek();
            if (c == ']') {
                next();
                break;
            }
            if (c != ',')
                throw new RuntimeException("Expected ',' or ']' in array, got " + c + " at " + i);
            next();
        }
        return new JsonArray(elems);
    }
}

public interface Json {
    static Json parse(String s) {
        return Parser.parseJson(s.trim());
    }

    static JsonObject parseObject(String s) {
        return (JsonObject) parse(s);
    }
}

record JsonString(String inner) implements Json {
    @Override
    public String toString() {
        return inner;
    }
}

record JsonDouble(double inner) implements Json {
    @Override
    public String toString() {
        return "" + inner;
    }
}

record JsonLong(long inner) implements Json {
    @Override
    public String toString() {
        return "" + inner;
    }
}

record JsonBoolean(boolean inner) implements Json {
    @Override
    public String toString() {
        return "" + inner;
    }
}

record JsonNull() implements Json {
    @Override
    public String toString() {
        return "null";
    }
}

record JsonPair(JsonString left, Json right) implements Json {
    @Override
    public String toString() {
        return left + ": " + right;
    }
}

record JsonObject(List<JsonPair> fields) implements Json { // json spec "allows" duplicates
    @Override
    public String toString() {
        return "{" + fields.stream().map(Json::toString).collect(Collectors.joining(", ")) + "}";
    }

    public Json get(String rawKey) {
        return fields.stream().filter(pair -> pair.left().inner().equals(rawKey)).findFirst().map(JsonPair::right)
                .orElse(null);
    }

    public JsonObject getObj(String rawKey) {
        Json obj = get(rawKey);
        if (obj instanceof JsonNull)
            return null;
        return (JsonObject) obj;
    }

    public JsonArray getArray(String rawKey) {
        Json obj = get(rawKey);
        if (obj instanceof JsonNull)
            return null;
        return (JsonArray) obj;
    }

    public String getString(String rawKey) {
        Json obj = get(rawKey);
        if (obj == null || obj instanceof JsonNull)
            return null;
        if (obj instanceof JsonString js)
            return js.inner();
        if (obj instanceof JsonLong jl)
            return String.valueOf(jl.inner());
        if (obj instanceof JsonDouble jd)
            return String.valueOf(jd.inner());
        return obj.toString();
    }

    public double getDouble(String rawKey) {
        Json obj = get(rawKey);
        return switch (obj) {
            case null -> 0.0;
            case JsonNull jsonNull -> 0.0;
            case JsonLong(long inner) -> inner;
            case JsonString(String inner) -> Double.parseDouble(inner);
            default -> ((JsonDouble) obj).inner();
        };
    }

    public long getLong(String rawKey) {
        Json obj = get(rawKey);
        if (obj == null || obj instanceof JsonNull)
            return 0L;
        return ((JsonLong) obj).inner();
    }
}

record JsonArray(List<Json> elements) implements Json {
    @Override
    public String toString() {
        return "[" + elements.stream().map(Json::toString).collect(Collectors.joining(", ")) + "]";
    }
}
