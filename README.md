# Connect Four bot — Java starter

Zero dependencies. JDK standard library only (`com.sun.net.httpserver`) —
nothing to install beyond a JDK.

Prerequisite: **JDK 17+**.

## Quick start

```bash
javac *.java && java Server
```

The server listens on port 8000 (or `$PORT` if set). In another terminal:

```bash
curl -X POST http://localhost:8000/move \
  -H "Content-Type: application/json" \
  -d '{"you":1,"board":[[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0],[0,0,0,0,0,0,0,0]],"moves":[],"game":{"match_id":"local","game_number":1,"clock_remaining_ms":5000}}'
```

You should get back something like `{"column": 4}`.

## What to edit

Open `Bot.java` and change the `chooseMove(board, you, info)` function.
That's it. `Server.java` is the HTTP server (CORS headers, JSON parsing,
error handling); it calls your function once per turn, and you shouldn't
need to touch it. `Json.java` is a small JSON parser/writer used by
`Server.java`, and `MoveInfo.java` is the record described below — you
shouldn't need to touch either.

- `board[col][row]` — column 0 is the left edge, row 0 is the bottom.
- `0` = empty, `1` = player 1's piece, `2` = player 2's piece.
- Return the column (0–7) you want to drop into. It must not be full.
- `info` (`MoveInfo`) is extra context you can ignore: `moves()` (the move
  history), `matchId()`, `gameNumber()`, and `clockRemainingMs()` (your
  remaining think-time budget for **this game**, not this move).

See the root [`README.md`](../../README.md) for the full contract (request/response
shape, clock rules, failure rules).

## Docker

This is how the arena will actually run your bot, so it's worth testing:

```bash
docker build -t my-bot .
docker run -p 8000:8000 my-bot
```

## CI smoke test

`.github/workflows/smoke.yml` builds your Docker image, starts it, POSTs the
same sample request as above, and fails the build if the response isn't a
legal column. Push to GitHub and check the Actions tab.
