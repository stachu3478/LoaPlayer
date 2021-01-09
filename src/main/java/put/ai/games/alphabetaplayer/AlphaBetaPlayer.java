/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package put.ai.games.alphabetaplayer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import put.ai.games.game.Board;
import put.ai.games.game.Move;
import put.ai.games.game.Player;

public class AlphaBetaPlayer extends Player {
    private final Random random = new Random(0xdeadbeef);
    private int depth;
    private int maxDepth;
    private Color me;
    private final String[] jokes = {
        "Wybierz najkrótszą ścieżkę do zwycięstwa i nią podążaj!",
        "Połączmy siły we wspólną potęgę!",
        "Niech eksplozja rozwiązań Cię nie ogranicza!",
        "Tylko z jednego drzewa rekurencyjnego wyrasta korzeń chwały!",
        "Ty jesteś pionkiem, a ja jestem algorytmem!",
        "Ja nie myślę, tylko przeszukuję szeroki wachlarz możliwości!",
        "Nie mów \"Następny gracz\"!",
        "Kto nie koduje, ten nie wygrywa!",
        "Podziel się darmowym obiadem!",
        "../../../../nie/bój/się/rekurencji\n../../../../nie/bój/się/rekurencji\n../../../../nie/bój/się/rekurencji\n../../../../nie/bój/się/rekurencji\n../../../../nie/bój/się/rekurencji\n",
        "A ty jakiej używasz heurystyki?",
        "Szczelił liścia i zrobił piękny nawrót",
        "Nie rób drugiemu co Tobie ułatwia obliczenia",
        "Do stu procesorów! Aleś ty naiwny!",
            "Wiesz dlaczego wygrywam? Bo nie biorę porażki pod uwagę!",
    };

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board board) {
        Board b = board.clone();
        List<Move> moves = b.getMovesFor(getColor());
        Move shortestToWin = moves.get(0);
        Timer timer = new Timer();
        final AtomicBoolean[] timeAvailable = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeAvailable[0].set(false);
            }
        }, getTime() - 1000);
        maxDepth = Integer.MAX_VALUE;
        me = getColor();
        while (timeAvailable[0].get()) {
            Move move = moves.get(random.nextInt(moves.size()));
            depth = 0;
            if (canBeWinning(b, move, getColor()) && maxDepth > depth) {
                shortestToWin = move;
                maxDepth = depth;
            }
            if (maxDepth == 1) return move;
        }
        System.out.println(jokes[random.nextInt(jokes.length)]);
        return shortestToWin;
    }

    private boolean canBeWinning(Board b, Move move, Color current) {
        if (depth >= maxDepth) return false;
        b.doMove(move);
        Color opponent = getOpponent(current);
        Color winner = b.getWinner(opponent);
        boolean foundWinning;
        if (winner != null) {
            foundWinning = winner == me;
        } else {
            depth++;
            List<Move> moves = b.getMovesFor(opponent);
            foundWinning = canBeWinning(b, moves.get(random.nextInt(moves.size())), opponent);
        }
        b.undoMove(move);
        return foundWinning;
    }

    private class WeightedRandom {
        private float[] weights;

        public WeightedRandom(int size) {

        }
    }
}
