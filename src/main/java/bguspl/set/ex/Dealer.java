package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private int[] winingSet = new int[3];
    private Object winner;
    private long resetTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        Arrays.fill(winingSet, -1);
        resetTime = System.currentTimeMillis();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            table.switchingCards = false;
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        for (int i = 0;  i < winingSet.length; i++) {
            if(winingSet[i] != -1){
                // for (int j = 0; j < players.length; j++) {
                //     if (table.playersToTokens[j][winingSet[i]] == 1) {
                //         players[j].keyPressed(i);
                //     }
                // }
                table.removeCard(winingSet[i]);
                winingSet[i] = -1;
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private synchronized void placeCardsOnTable() {
        // TODO implement
            Collections.shuffle(deck);
            synchronized (table) {
                for (int i = 0 ; i < 12 ; i++) {
                    int rndCard = (int)(Math.random() * deck.size());
                        if (table.slotToCard[i] == null && !deck.isEmpty()) {
                            int card = deck.remove(rndCard);
                            table.placeCard(card, i);
                        }
                }
            }
            
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        synchronized (this) {
            try{
//                long timeToSleep = System.currentTimeMillis();
//                while(table.setAnnuncments.isEmpty() || System.currentTimeMillis() - timeToSleep < 900){
//                    this.wait();
//                }
                table.fairSemaphore.acquire();
                if(table.setAnnuncments.size() > 1) {
                    int playerId = table.setAnnuncments.poll();
                    int[] cardsToCheck = new int[3];
                    int j = 0;
                    for (int i = 0; i < 12; i++) {
                        if (table.playersToTokens[playerId][i] == 1) {
                            cardsToCheck[j] = table.slotToCard[i];
                            j++;
                        }
                    }
                    if(env.util.testSet(cardsToCheck)) {
                        players[playerId].point();
                        winingSet = cardsToCheck;
                        updateTimerDisplay(true);
                    } else {
                        players[playerId].penalty();
                    }
                    table.shouldWait = false;
                }
                table.fairSemaphore.release();
                this.notifyAll();
            } catch (InterruptedException ignored) {

            }

        }
        //modify sync (PS6) 
        //this is done by a player that placed 3 tokens (check for set and give a point or penalty for player) or by timeout (reshuffle deck)
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        long currentTimeLeft = env.config.turnTimeoutMillis + (resetTime - System.currentTimeMillis());
        if(reset) {
            resetTime = System.currentTimeMillis();
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } else if(currentTimeLeft > 0) {
            env.ui.setCountdown(currentTimeLeft, currentTimeLeft <= env.config.turnTimeoutWarningMillis);
        } else {
            removeAllCardsFromTable();
            placeCardsOnTable();
            updateTimerDisplay(true);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        table.switchingCards = true;
        synchronized (table) {
            for (int i = 0; i < 12; i++) {
                if (table.slotToCard[i] != null) {
                    int card = table.slotToCard[i];
                    table.removeCard(i);
                    deck.add(card);
                }
            }
        }
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = 0;
        ConcurrentSkipListSet<Integer> winners = new ConcurrentSkipListSet<>();
        for(Player player:players) {
            if(player.score() > max) {
                max = player.score();
                winners.clear();
                winners.add(player.id);
            } else if(player.score() == max) {
                winners.add(player.id);
            }
        }
        int[] winnersId = new int[winners.size()];
        int i = 0;
        for (Integer id : winners) {
            winnersId[i] = id;
            i++;
        }
        env.ui.announceWinner(winnersId);
    }
}
