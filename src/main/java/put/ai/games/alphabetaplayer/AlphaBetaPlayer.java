/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;
import put.ai.games.linesofaction.LinesOfActionMove;

public class AlphaBetaPlayer extends Player {
    private final Random random = new Random(0xdeadbeef);

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        Timer timer = new Timer();
        final AtomicBoolean[] timeLow = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeLow[0].set(false);
            }
        }, getTime() - 100);
        return new AlphaBeta(b, getColor(), () -> timeLow[0].get(), random).process();
    }

    public interface TimeWatchdog {
        boolean call();
    }

    public static class TimeoutException extends RuntimeException {}

    private static class AlphaBeta {
        private final TimeWatchdog watchdog;
        private final Board board;
        private final Color me;
        private final Random random;
        private Move bestMove;
        private int depth;
        private Set<Board> dopedMyBoards = new HashSet<>();
        private Set<Board> dopedOpBoards = new HashSet<>();
        private BoardIndexer indexer;

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog, Random random) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
            this.random = random;
            this.depth = 1;
            this.indexer = new BoardIndexer();
        }

        public Move process() {
            depth = Math.max(1, depth - 3);
            Move bestDepthMove = null;
            try {
                while (watchdog.call()) {
                    bestMove = null;
                    float bestVal = run(me, depth, -Float.MAX_VALUE, Float.MAX_VALUE);
                    System.out.println("Best val: " + bestVal);
                    if (bestMove != null) bestDepthMove = bestMove;
                    if (bestVal == Float.MAX_VALUE) return bestDepthMove;
                    if (bestVal == -Float.MAX_VALUE) return bestDepthMove;
                    depth++;
                }
            } catch (TimeoutException e) {
                System.out.println("Depth reached " + depth);
            }
            if (bestDepthMove == null) {
                List<Move> moves = board.getMovesFor(me);
                return moves.get(random.nextInt(moves.size()));
            }
            return bestDepthMove;
        }

        private float run(Color color, int depth, float alpha, float beta)
        {
            if (!watchdog.call()) {
                dopedOpBoards.clear();
                dopedMyBoards.clear();
                throw new TimeoutException();
            }
            Set<Board> dopedBoards = getDopedBoards(color);
            if (!dopedBoards.add(board)) return alpha;
            Color winner = board.getWinner(color);
            if (winner != null) {
                dopedBoards.remove(board);
                return winner == color ? Float.MAX_VALUE : -Float.MAX_VALUE;
            }
            if( depth == 0 ) {
                dopedBoards.remove(board);
                return indexer.heuri(color); //color == me ? poll : -poll;
            }
            List<Move> moves = board.getMovesFor(color);
            for(Move move : moves) {
                indexer.doMove((LinesOfActionMove) move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                indexer.undoMove((LinesOfActionMove) move);
                if( val > alpha ) {
                    if (this.depth == depth) bestMove = move;
                    alpha = val; // alpha=max(val,alpha);
                }
                if( alpha == Float.MAX_VALUE ) {
                    dopedBoards.remove(board);
                    return beta; // cutoff
                }
            } //endfor
            dopedBoards.remove(board);
            return alpha;
        }

        private Set<Board> getDopedBoards(Color color) {
            if (color == me) return dopedMyBoards;
            return dopedOpBoards;
        }

        private class BoardIndexer { // move search booster
            private final int[] mixRows;
            private final int[] mixCols;
            private final int[] mixLurdWedges;
            private final int[] mixLdruWedges;
            private final int boardSize;
            private final MooPoint[][] boardSnaphot;
            private final List<MooPoint> myPieces;
            private final List<MooPoint> opPieces;

            public BoardIndexer() {
                boardSize = board.getSize();
                mixRows = new int[boardSize];
                mixCols = new int[boardSize];
                mixLurdWedges = new int[boardSize * 2];
                mixLdruWedges = new int[boardSize * 2];
                boardSnaphot = new MooPoint[boardSize][boardSize];
                myPieces = new ArrayList<>();
                opPieces = new ArrayList<>();
                makeBoardSnapshot(board);
            }

            public float heuri(Color c) {
                if (c == me) return distanceSum(opPieces) - distanceSum(myPieces);
                return distanceSum(myPieces) - distanceSum(opPieces);
            }

            private float distanceSum(List<MooPoint> pieces) {
                float connections = 0;
                float wx = 0;
                float wy = 0;
                for (MooPoint p1 : pieces) {
                    wx += p1.getX();
                    wy += p1.getY();
                    for (MooPoint p2 : pieces) {
                        if (Math.max(Math.abs(p1.getX() - p2.getX()), Math.abs(p1.getY() - p2.getY())) <= 1) connections--;
                    }
                }
                connections /= pieces.size();
                connections *= boardSize;
                wx /= pieces.size();
                wy /= pieces.size();
                for (MooPoint p1 : pieces) {
                    connections += Math.hypot(p1.getX() - wx, p1.getY() - wy);
                }
                return connections;
            }

            private void makeBoardSnapshot(Board b) {
                myPieces.clear();
                opPieces.clear();
                for (int x = 0; x < boardSize; x++) {
                    mixRows[x] = 0;
                    mixCols[x] = 0;
                    mixLurdWedges[x] = 0;
                    mixLdruWedges[x] = 0;
                }
                for (int x = 0; x < boardSize; x++) {
                    for (int y = 0; y < boardSize; y++) {
                        Color color = b.getState(x, y);
                        if (color != Color.EMPTY) {
                            MooPoint point = new MooPoint(x, y);
                            boardSnaphot[x][y] = point;
                            if (color == me) {
                                myPieces.add(point);
                            } else {
                                opPieces.add(point);
                            }
                            changeMixes(x, y, 1);
                        }
                    }
                }
            }

            public void doMove(LinesOfActionMove move) {
                int sx = move.getSrcX();
                int sy = move.getSrcY();
                int dx = move.getDstX();
                int dy = move.getDstY();
                MooPoint piece = boardSnaphot[sx][sy];
                piece.setX(dx);
                piece.setY(dy);
                MooPoint targetPiece = boardSnaphot[dx][dy];
                Color color = board.getState(dx, dy);
                if (targetPiece != null) {
                    if (color == me) {
                        myPieces.remove(targetPiece);
                    } else {
                        opPieces.remove(targetPiece);
                    }
                } else {
                    changeMixes(dx, dy, 1);
                }
                boardSnaphot[sx][sy] = null;
                boardSnaphot[dx][dy] = piece;
                board.doMove(move);
                changeMixes(sx, sy, -1);
            }

            public void undoMove(LinesOfActionMove move) {
                int sx = move.getSrcX();
                int sy = move.getSrcY();
                int dx = move.getDstX();
                int dy = move.getDstY();
                MooPoint piece = boardSnaphot[dx][dy];
                piece.setX(sx);
                piece.setY(sy);
                boardSnaphot[sx][sy] = piece;
                board.undoMove(move);
                Color color = board.getState(dx, dy);
                if (color != Color.EMPTY) {
                    MooPoint point = new MooPoint(dx, dy);
                    boardSnaphot[dx][dy] = point;
                    if (color == me) {
                        myPieces.add(point);
                    } else {
                        opPieces.add(point);
                    }
                } else {
                    changeMixes(dx, dy, -1);
                    boardSnaphot[dx][dy] = null;
                }
                changeMixes(sx, sy, 1);
            }

            private void changeMixes(int x, int y, int val) {
                mixRows[y] += val;
                mixCols[x] += val;
                mixLurdWedges[boardSize + x - y] += val;
                mixLdruWedges[x + y] += val;
            }

            private class MooPoint {
                private int x;
                private int y;

                public MooPoint(int x, int y) {
                    this.x = x;
                    this.y = y;
                }

                public void setX(int x) {
                    this.x = x;
                }

                public int getX() {
                    return x;
                }

                public void setY(int y) {
                    this.y = y;
                }

                public int getY() {
                    return y;
                }
            }
        }
    }

}
