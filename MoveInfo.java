/**
 * Extra per-move context, passed as {@code chooseMove}'s third argument.
 * Safe to ignore -- the default bot doesn't use it.
 *
 * @param moves             the full move history so far, as column numbers
 * @param matchId           match identifier
 * @param gameNumber        which game this is within the match
 * @param clockRemainingMs  your remaining think-time budget for THIS GAME
 *                          (not this move) -- see the root README's
 *                          "Time control" section
 */
public record MoveInfo(int[] moves, String matchId, int gameNumber, long clockRemainingMs) {
}
