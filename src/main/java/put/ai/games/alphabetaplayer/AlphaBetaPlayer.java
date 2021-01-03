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

    private Random random = new Random(0xdeadbeef);


    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }


    @Override
    public Move nextMove(Board b) {
        return alpha(b);
    }

    private Move alpha(Board b) {
        List<Move> myMoves = b.getMovesFor(getColor());
        Move bestMove = myMoves.get(0);
        int bestRank = 0;
        for (Move m : myMoves) {
            int r = rank(m);
            if (r > bestRank) {
                bestRank = r;
                bestMove = m;
            }
        }
        return bestMove;
    }

    private int rank(Board b, Move m) {
        b.doMove(m);
        // ranking code goes here
        b.undoMove(m);
        return 0;
    }
}
