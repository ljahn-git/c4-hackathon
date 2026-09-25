import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny recursive-descent JSON parser and writer. The JDK doesn't ship one,
 * and pulling in a library would break the "zero dependencies" promise, so
 * Server.java uses this instead.
 *
 * Supports objects, arrays, strings (with escapes), numbers, true/false/null.
 * Parsed values are plain Java objects: Map<String,Object>, List<Object>
 * String, Double, Boolean, or null.
 */
final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
        this.pos = 0;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw new JsonException("Unexpected trailing content at position " + parser.pos);
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= text.length()) {
            throw new JsonException("Unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                if (c == '-' || Character.isDigit(c)) {
                    return parseNumber();
                }
                throw new JsonException("Unexpected character '" + c + "' at position " + pos);
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("Expected string key at position " + pos);
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == '}') {
                pos++;
                break;
            } else {
                throw new JsonException("Expected ',' or '}' at position " + pos);
            }
        }
        return result;
    }

    private List<Object> parseArray() {
        List<Object> result = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(parseValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == ']') {
                pos++;
                break;
            } else {
                throw new JsonException("Expected ',' or ']' at position " + pos);
            }
        }
        return result;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new JsonException("Unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw new JsonException("Unterminated escape sequence");
                }
                char esc = text.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new JsonException("Invalid unicode escape");
                        }
                        String hex = text.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        throw new JsonException("Invalid escape character '" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
            pos++;
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        String number = text.substring(start, pos);
        if (number.isEmpty() || number.equals("-")) {
            throw new JsonException("Invalid number at position " + start);
        }
        return Double.parseDouble(number);
    }

    private void expectLiteral(String literal) {
        if (pos + literal.length() > text.length() || !text.regionMatches(pos, literal, 0, literal.length())) {
            throw new JsonException("Expected '" + literal + "' at position " + pos);
        }
        pos += literal.length();
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new JsonException("Unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw new JsonException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    /** Writes a small object of String -> Object (String, Number, or nested Map/List) as JSON. */
    static String writeObject(Map<String, Object> obj) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, entry.getKey());
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            throw new JsonException("Cannot serialize value of type " + value.getClass());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    static final class JsonException extends RuntimeException {
        JsonException(String message) {
            super(message);
        }
    }
}
