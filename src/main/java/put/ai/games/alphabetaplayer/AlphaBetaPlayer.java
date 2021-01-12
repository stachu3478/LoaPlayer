/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.awt.*;
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
        private final MovePoller poller;
        private Move bestMove;
        private int depth;
        private final Set<Board> dopedMyBoards = new HashSet<>();
        private final Set<Board> dopedOpBoards = new HashSet<>();
        private final BoardIndexer indexer;

        public AlphaBeta(Board board, Color me, TimeWatchdog watchdog, Random random) {
            this.board = board.clone();
            this.watchdog = watchdog;
            this.me = me;
            this.random = random;
            this.poller = new MovePoller(me);
            this.depth = 1;
            this.indexer = new BoardIndexer();
        }

        public Move process() {
            depth = Math.max(1, depth - 3);
            Move bestDepthMove = null;
            this.poller.setMaxDepth(Integer.MAX_VALUE);
            try {
                while (watchdog.call()) {
                    bestMove = null;
                    float bestVal = run(me, depth, -Float.MAX_VALUE, Float.MAX_VALUE);
                    System.out.println("Best val: " + bestVal);
                    if (bestMove != null) bestDepthMove = bestMove;
                    if (bestVal == Float.MAX_VALUE) return bestDepthMove;
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
            Color winner = indexer.getWinner(color, getOpponent(color));
            if (winner != null) {
                dopedBoards.remove(board);
                return winner == color ? Float.MAX_VALUE : -Float.MAX_VALUE;
            }
            List<Move> moves = board.getMovesFor(color);
            if( depth == 0 ) {
                float poll = 0;
                for (int i = 0; i < 1 && watchdog.call(); i++) {
                    poll += poller.poll(moves, board);
                }
                dopedBoards.remove(board);
                return color == me ? poll : -poll;
            }
            for(Move move : moves) {
                indexer.doMove((LinesOfActionMove) move);
                float val = -run(getOpponent(color), depth - 1, -beta, -alpha);
                indexer.undoMove((LinesOfActionMove) move);
                if( val > alpha ) {
                    if (this.depth == depth) bestMove = move;
                    alpha = val; // alpha=max(val,alpha);
                }
                if( alpha >= beta ) {
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

        private class MovePoller {
            private Board board;
            private final Color me;
            private int depth;
            private int maxDepth;

            public MovePoller(Color c) {
                this.me = c;
            }

            public void setMaxDepth(int maxDepth) {
                this.maxDepth = maxDepth;
            }

            public float poll(List<Move> moves, Board b) {
                this.board = b;
                maxDepth = Integer.MAX_VALUE;
                depth = 0;
                float winningWeight = canBeWinning((LinesOfActionMove) moves.get(random.nextInt(moves.size())), me);
                if (winningWeight > 0 && depth < maxDepth) {
                    maxDepth = depth;
                }
                return winningWeight / depth;
            }

            private int canBeWinning(LinesOfActionMove move, Color current) {
                if (depth >= maxDepth) return 0;
                indexer.doMove(move);
                depth++;
                Color opponent = getOpponent(current);
                Color winner = board.getWinner(opponent);
                int foundWinning = 0;
                if (winner != null) {
                    foundWinning = winner == me ? 1 : -100;
                } else {
                    LinesOfActionMove newMove = indexer.getRandomMove(opponent, current);
                    if (newMove != null) foundWinning = canBeWinning(newMove, opponent);
                }
                indexer.undoMove(move);
                return foundWinning;
            }
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

            public Color getWinner(Color me, Color op) {
                if (winning(myPieces)) return me;
                if (winning(opPieces)) return op;
                return null;
            }

            private boolean winning(List<MooPoint> pieces) {
                int connectedCountdown = pieces.size() - 1;
                if (connectedCountdown <= 0) return true;
                Set<MooPoint> closed = new HashSet<>();
                closed.add(pieces.get(0));
                List<MooPoint> connectedPieces = new ArrayList<>();
                connectedPieces.add(pieces.get(0));
                while (!connectedPieces.isEmpty()) {
                    List<MooPoint> newPieces = new ArrayList<>();
                    for (MooPoint p1 : connectedPieces) {
                        for (MooPoint p2 : pieces) {
                            if (p1 == p2) continue;
                            if (closed.contains(p2)) continue;
                            if (Math.max(Math.abs(p1.getX() - p2.getX()), Math.abs(p1.getY() - p2.getY())) == 1) {
                                newPieces.add(p2);
                                closed.add(p2);
                                connectedCountdown--;
                                if (connectedCountdown == 0) return true;
                            }
                        }
                    }
                    connectedPieces = newPieces;
                }
                return false;
            }

            public LinesOfActionMove getRandomMove(Color color, Color blocker) {
                List<MooPoint> pieces = color == me ? myPieces : opPieces;
                int maxRand = pieces.size() * 8;
                int rand = random.nextInt(maxRand);
                for (int i = 0; i < maxRand; i++) {
                    rand = (rand + 1) % maxRand;
                    MooPoint piece = pieces.get(rand / 8);
                    int x = piece.getX();
                    int y = piece.getY();
                    if (boardSnaphot[x][y] != piece) {
                        System.out.print("?");
                        continue;
                    }
                    if (board.getState(x, y) != color) {
                        System.out.print("!");
                        continue;
                    }
                    int rest = rand % 8;
                    int xDir = -1;
                    int yDir = -1;
                    int distance = mixLdruWedges[x + y];
                    if (rest == 0) {
                        xDir = 0;
                    } else if (rest == 1) {
                        xDir = 1;
                    } else if (rest == 2) {
                        xDir = 1;
                        yDir = 0;
                    } else if (rest == 3) {
                        xDir = 1;
                        yDir = 1;
                    } else if (rest == 4) {
                        xDir = 0;
                        yDir = 1;
                    } else if (rest == 5) {
                        yDir = 1;
                    } else if (rest == 6) {
                        yDir = 0;
                    }

                    if (xDir == 0) {
                        distance = mixCols[x];
                    } else if (yDir == 0) {
                        distance = mixRows[y];
                    } else if (xDir != yDir) {
                        distance = mixLurdWedges[boardSize + x - y];
                    }

                    int dx = x + distance * xDir;
                    int dy = y + distance * yDir;
                    if (dx < 0 || dy < 0 || dx >= boardSize || dy >= boardSize) continue;
                    Color state = board.getState(x + distance * xDir, y + distance * yDir);
                    if (state == color) continue; // cannot remove himself
                    boolean canMove = true;
                    for (int j = 1; j < distance; j++) { // enemy piece blocks
                        state = board.getState(x + j * xDir, y + j * yDir);
                        if (state == blocker)  {
                            canMove = false;
                            break;
                        }
                    }
                    if (!canMove) continue;
                    return new LinesOfActionMove(color, x, y, dx, dy);
                }
                System.out.println("Warning: No move found");
                return null;
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
