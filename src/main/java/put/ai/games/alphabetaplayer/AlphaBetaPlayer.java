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
    };

    @Override
    public String getName() {
        return "Stanisław Graczyk 146889 Wojciech Kamiński 141242";
    }

    @Override
    public Move nextMove(Board b) {
        Timer timer = new Timer();
        final AtomicBoolean[] timeAvailable = {new AtomicBoolean(true)};
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timeAvailable[0].set(false);
            }
        }, getTime() - 1000);
        List<Move> moves = b.getMovesFor(getColor());
        Move shortestToWin = moves.get(0);
        int shortestLength = Integer.MAX_VALUE;
        while (timeAvailable[0].get()) {
            Move move = moves.get(random.nextInt(moves.size()));
            depth = 0;
            if (canBeWinning(b, move, getColor()) && shortestLength > depth) {
                shortestToWin = move;
                shortestLength = depth;
            }
        }
        System.out.println(jokes[random.nextInt(jokes.length)]);
        return shortestToWin;
    }

    private boolean canBeWinning(Board b, Move move, Color current) {
        if (b.getWinner(current) == getColor()) return true;
        b.doMove(move);
        depth++;
        List<Move> moves = b.getMovesFor(getOpponent(current));
        if (moves.size() == 0) return false;
        boolean foundWinning = canBeWinning(b, moves.get(random.nextInt(moves.size())), getOpponent(current));
        b.undoMove(move);
        return foundWinning;
    }
}
