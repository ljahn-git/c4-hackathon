import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP server for your Connect Four bot (Java, JDK stdlib only; nothing to
 * build beyond `javac`).
 *
 * You shouldn't need to edit this file. Write your bot in Bot.java: this
 * server handles HTTP, CORS headers, and JSON parsing, then calls
 * `Bot.chooseMove` once per turn.
 *
 * Run it:
 *   javac *.java && java Server
 * Then test it:
 *   curl -X POST http://localhost:8000/move \
 *     -H "Content-Type: application/json" \
 *     -d '{"you":1,"board":[[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0]],"moves":[],"game":{"match_id":"local","game_number":1,"clock_remaining_ms":5000}}'
 */
public class Server {
    // Columns that aren't full yet. Kept separate from Bot.java so the
    // server's safety check still works however you change your bot.
    private static List<Integer> legalColumns(int[][] board) {
        List<Integer> result = new ArrayList<>();
        for (int col = 0; col < board.length; col++) {
            int[] column = board[col];
            if (column[column.length - 1] == 0) {
                result.add(col);
            }
        }
        return result;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        // Chrome Private Network Access: lets a hosted https:// page call
        // http://localhost during local testing.
        headers.add("Access-Control-Allow-Private-Network", "true");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        addCorsHeaders(exchange);
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        } else {
            exchange.getResponseBody().close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        send(exchange, status, Json.writeObject(body));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static int[][] parseBoard(Object raw) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("\"board\" must be an array");
        }
        List<Object> columns = (List<Object>) raw;
        int[][] board = new int[columns.size()][];
        for (int col = 0; col < columns.size(); col++) {
            Object rawColumn = columns.get(col);
            if (!(rawColumn instanceof List)) {
                throw new IllegalArgumentException("\"board\" columns must be arrays");
            }
            List<Object> rows = (List<Object>) rawColumn;
            int[] column = new int[rows.size()];
            for (int row = 0; row < rows.size(); row++) {
                Object cell = rows.get(row);
                if (!(cell instanceof Number)) {
                    throw new IllegalArgumentException("board cells must be numbers");
                }
                column[row] = ((Number) cell).intValue();
            }
            board[col] = column;
        }
        return board;
    }

    private static int parseYou(Object raw) {
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException("\"you\" must be a number");
        }
        return ((Number) raw).intValue();
    }

    @SuppressWarnings("unchecked")
    private static MoveInfo parseMoveInfo(Map<String, Object> request) {
        List<Integer> movesList = (List<Integer>) request.getOrDefault("moves", new ArrayList<Integer>());
        int[] moves = new int[movesList.size()];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = ((Number) movesList.get(i)).intValue();
        }

        Map<String, Object> game = (Map<String, Object>) request.getOrDefault("game", new LinkedHashMap<String, Object>());
        String matchId = game.get("match_id") != null ? game.get("match_id").toString() : "";
        int gameNumber = game.get("game_number") != null ? ((Number) game.get("game_number")).intValue() : 0;
        long clockRemainingMs = game.get("clock_remaining_ms") != null
                ? ((Number) game.get("clock_remaining_ms")).longValue()
                : 0L;

        return new MoveInfo(moves, matchId, gameNumber, clockRemainingMs);
    }

    private static class MoveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                send(exchange, 204, null);
                return;
            }

            if ("GET".equalsIgnoreCase(method) && "/health".equals(path)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", "ok");
                sendJson(exchange, 200, body);
                return;
            }

            if ("POST".equalsIgnoreCase(method) && "/move".equals(path)) {
                try {
                    String raw = readBody(exchange);
                    Object parsed = Json.parse(raw);
                    if (!(parsed instanceof Map)) {
                        throw new IllegalArgumentException("request body must be a JSON object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = (Map<String, Object>) parsed;

                    int[][] board = parseBoard(request.get("board"));
                    int you = parseYou(request.get("you"));
                    MoveInfo info = parseMoveInfo(request);

                    int column = Bot.chooseMove(board, you, info);

                    if (!legalColumns(board).contains(column)) {
                        throw new IllegalStateException("chooseMove returned an illegal column: " + column);
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("column", column);
                    sendJson(exchange, 200, body);
                } catch (Throwable exc) {
                    // Throwable, not Exception: a StackOverflowError from a deep
                    // recursive search must still produce a response.
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("error", exc.getMessage() != null ? exc.getMessage() : exc.toString());
                    sendJson(exchange, 500, body);
                }
                return;
            }

            send(exchange, 404, null);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 8000;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            port = Integer.parseInt(envPort.trim());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new MoveHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        System.out.println("Bot listening on http://0.0.0.0:" + port);
        server.start();
    }
}
