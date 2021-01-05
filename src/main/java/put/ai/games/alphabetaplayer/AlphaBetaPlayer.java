/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.List;
import java.util.Random;
import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;

public class AlphaBetaPlayer extends Player {
    private BoardIndexer indexer;
    private boolean init = false;

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        if (!init) doInit(b);
        return alpha(b.clone());
    }

    private void doInit(Board b) {
        init = true;
        indexer = new BoardIndexer(b);
    }

    private Move alpha(Board b) {
        List<Move> myMoves = b.getMovesFor(getColor());
        Move bestMove = myMoves.get(0);
        int bestRank = Integer.MIN_VALUE;
        boolean replaced = false;
        for (Move m : myMoves) {
            int r = rank(b, m);
            if (r > bestRank) {
                bestRank = r;
                bestMove = m;
                replaced = true;
            }
        }
        if (bestRank == 0) System.out.println("Warning: Zero valued rank");
        if (!replaced) System.out.println("Warning: Zero indexed move");
        System.out.println("test");
        return bestMove;
    }

    private int rank(Board b, Move m) {
        b.doMove(m);
        indexer.fetch(b);
        // ranking code goes here
        int ranking = groupState(getColor()) - groupState(getOpponent(getColor()));
        b.undoMove(m);
        return ranking;
    }

    private int groupState(Color color) {
        int state = 0;
        for (int x = 0; x < indexer.getSize(); x++) {
            for (int y = 0; y < indexer.getSize(); y++) {
                if (color != indexer.getColor(x, y)) continue;
                state -= 8;
                state += indexer.getConnectable(color, x, y, getOpponent(getColor()));
            }
        }
        return state;
    }
}
