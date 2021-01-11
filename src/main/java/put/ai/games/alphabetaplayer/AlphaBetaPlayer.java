/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.Point;
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
    public Move nextMove(Board board) {
        Board b = board.clone();
        Timer timer = new Timer();
        final AtomicBoolean[] timeAvailable = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeAvailable[0].set(false);
            }
        }, getTime() - 1000);
        return new MovePoller(b, timeAvailable).poll();
    }

    private class MovePoller {
        private final int[] polls;
        private final List<Move> moves;
        private final AtomicBoolean[] isTimeAvailable;
        private final Board board;
        private int totalPolls = 0;
        private final Color me;
        private int depth;
        private int maxDepth;
        private final BoardIndexer indexer;

        public MovePoller(Board b, AtomicBoolean[] isTimeAvailable) {
            this.me = getColor();
            moves = b.getMovesFor(me);
            this.polls = new int[moves.size()];
            this.isTimeAvailable = isTimeAvailable;
            this.board = b;
            this.indexer = new BoardIndexer(b);
        }

        public Move poll() {
            maxDepth = Integer.MAX_VALUE;
            while (isTimeAvailable[0].get()) {
                for (int i = 0; i < polls.length; i++) {
                    Move move = moves.get(i);
                    depth = 0;
                    indexer.makeBoardSnapshot(board);
                    int winningWeight = canBeWinning((LinesOfActionMove) move, me);
                    polls[i] += winningWeight;
                    if (winningWeight > 0 && depth < maxDepth) {
                        maxDepth = depth;
                        if (maxDepth <= 1) return move;
                    }
                    totalPolls++;
                    if (!isTimeAvailable[0].get()) break;
                }
            }
            int bestPoll = 0;
            int bestVal = Integer.MIN_VALUE;
            for (int i = 0; i < polls.length; i++) {
                if (polls[i] > bestVal) {
                    bestPoll = i;
                    bestVal = polls[i];
                }
            }
            System.out.println("Total polls: " + totalPolls);
            return moves.get(bestPoll);
        }

        private int canBeWinning(LinesOfActionMove move, Color current) {
            if (depth >= maxDepth) return 0;
            if (!isTimeAvailable[0].get()) return 0;
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

        private class BoardIndexer { // move search booster
            private final int[] mixRows;
            private final int[] mixCols;
            private final int[] mixLurdWedges;
            private final int[] mixLdruWedges;
            private final int boardSize;
            private final MooPoint[][] boardSnaphot;
            private final List<MooPoint> myPieces;
            private final List<MooPoint> opPieces;

            public BoardIndexer(Board b) {
                boardSize = b.getSize();
                mixRows = new int[boardSize];
                mixCols = new int[boardSize];
                mixLurdWedges = new int[boardSize * 2];
                mixLdruWedges = new int[boardSize * 2];
                boardSnaphot = new MooPoint[boardSize][boardSize];
                myPieces = new ArrayList<>();
                opPieces = new ArrayList<>();
                makeBoardSnapshot(b);
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
                        MooPoint point = new MooPoint(x, y);
                        boardSnaphot[x][y] = point;
                        if (color != Color.EMPTY) {
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
                }
                boardSnaphot[sx][sy] = null;
                boardSnaphot[dx][dy] = piece;
                board.doMove(move);
                changeMixes(sx, sy, -1);
                changeMixes(dx, dy, -1);
            }

            public void undoMove(LinesOfActionMove move) {
                int dx = move.getSrcX();
                int dy = move.getSrcY();
                int sx = move.getDstX();
                int sy = move.getDstY();
                MooPoint piece = boardSnaphot[sx][sy];
                piece.setX(dx);
                piece.setY(dy);
                boardSnaphot[dx][dy] = piece;
                board.undoMove(move);
                Color color = board.getState(sx, sy);
                if (color != null) {
                    MooPoint point = new MooPoint(sx, sy);
                    boardSnaphot[sx][sy] = point;
                    if (color == me) {
                        myPieces.add(point);
                    } else {
                        opPieces.add(point);
                    }
                } else boardSnaphot[sx][sy] = null;
            }

            private void changeMixes(int x, int y, int val) {
                mixRows[y] += val;
                mixCols[x] += val;
                mixLurdWedges[boardSize + x - y] += val;
                mixLdruWedges[x + y] += val;
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
