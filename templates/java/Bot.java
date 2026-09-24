import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Your Connect Four bot. This is the only file you need to edit.
 *
 * The HTTP server lives in Server.java. It calls `chooseMove` once per turn
 * with the current board and sends your answer back to the arena.
 */
public class Bot {
    private static final Random RANDOM = new Random();

    // board:  an 8x8 array of columns, each an array of 8 rows.
    //         board[col][row]: col 0 is the LEFT column, row 0 is the BOTTOM row.
    //         0 = empty, 1 = player 1's piece, 2 = player 2's piece.
    // you:    1 or 2, which player you are this game.
    // info:   extra context, safe to ignore. See MoveInfo.java -- notably
    //         info.clockRemainingMs(), your remaining think-time budget for
    //         THIS GAME (not this move).
    //
    // Return: an int 0-7, the column you want to drop a piece into.
    //         It MUST be a legal (non-full) column; see `legalMoves` below.
    //
    // Every call gets the full game state, so don't rely on variables that
    // persist between calls: the arena may restart your bot mid-game.
    public static int chooseMove(int[][] board, int you, MoveInfo info) {
        List<Integer> moves = legalMoves(board);
        int[] offTemperature = {0, 0, 0, 0, 0, 0, 0, 0};
        int[] defTemperature = {0, 0, 0, 0, 0, 0, 0, 0};

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j] == you) {
                    offTemperature[i]++;
                } else if (board[i][j] != 0) {
                    defTemperature[i]++;
                }
            }
        }


        int moved = getBestMove(offTemperature, defTemperature, board, you, info);
        return moved;
    }

    public static int getBestMove(int[] offTemp, int[] defTemp, int[][] board, int you, MoveInfo info) {
        int bestMove = 0;
        int mode = RANDOM.nextInt(0,3);
        if (mode == 0) {
            // Defensive
            int maxIndex = 0;
            for(int i = 0; i < defTemp.length; i++) {
                if (defTemp[i] > maxIndex) {
                    maxIndex = defTemp[i];
                }
            }
            bestMove = maxIndex;
        }
        if (mode == 1 || mode == 2) {
            int maxIndex = 0;
            for(int i = 0; i < offTemp.length; i++) {
                if (offTemp[i] > maxIndex) {
                    maxIndex = offTemp[i];
                }
            }
            int diagonalChange = RANDOM.nextInt(0,3);
            if (diagonalChange == 0) {
                bestMove = maxIndex + 1;
            } else if (diagonalChange == 1) {
                bestMove = maxIndex - 1;
            }
        }

        if (bestMove > 7) {
            bestMove = 7;
        }
        if (bestMove < 0){
            bestMove = 0;
        }
        return bestMove;
    }

    public static List<Integer> legalMoves(int[][] board) {
        // Columns that aren't full yet (top row is still empty).
        List<Integer> moves = new ArrayList<>();
        for (int col = 0; col < board.length; col++) {
            int[] column = board[col];
            if (column[column.length - 1] == 0) {
                moves.add(col);
            }
        }
        return moves;
    }
}
