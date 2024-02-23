package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
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
    private int[] winningSet;
    public final Object actionLocker;
    public final Object setLocker;
    public final Object[] playerShouldWait;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        winningSet = new int[env.config.featureSize];
        Arrays.fill(winningSet, -1);
        reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
        actionLocker = new Object();
        setLocker = new Object();
        playerShouldWait = new Object[env.config.players];
        Arrays.fill(playerShouldWait, new Object());
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
        terminate = true;
        try {
            for (Player player : players) {
                player.terminate();
                player.getThread().interrupt();
                player.getThread().join();
            }
        } catch (InterruptedException ignored) {
        }
        Thread.currentThread().interrupt();
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
        for (int i = 0; i < winningSet.length; i++) {
            if (winningSet[i] != -1) {
                table.removeCard(table.cardToSlot[winningSet[i]]);
                winningSet[i] = -1;
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {// this was syncronized
        // TODO implement
        Collections.shuffle(deck);
        boolean changed = false;
        for (int i = 0; i < env.config.tableSize; i++) {
            int rndCard = (int) (Math.random() * deck.size());
            if (table.slotToCard[i] == null && !deck.isEmpty()) {
                int card = deck.remove(rndCard);
                table.placeCard(card, i);
                changed = true;
            }
        }
        table.switchingCards = false;
        synchronized (actionLocker) {
            actionLocker.notifyAll();
        }
        if (changed) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        Integer playerId = null;
        synchronized (setLocker) {
            try {
                long sleepTime = System.currentTimeMillis();
                if (env.config.turnTimeoutWarningMillis < reshuffleTime - sleepTime) { //out of warning time
                    while (table.setAnnouncements.isEmpty() && System.currentTimeMillis() - sleepTime < 900) {
                        setLocker.wait(900);
                    }
                } else { //in warning time
                    while (table.setAnnouncements.isEmpty() && System.currentTimeMillis() - sleepTime < 10) {
                        setLocker.wait(10);
                    }
                }
                playerId = table.setAnnouncements.poll();
                setLocker.notifyAll();
            } catch (InterruptedException ignored) {
            }
        }


        if (playerId != null) {
            synchronized (playerShouldWait[playerId]) {
                int[] cardsToCheck = new int[3];
                int j = 0;
                for (int i = 0; i < env.config.tableSize; i++) {
                    if (table.playersToTokens[playerId][i] == 1) {
                        cardsToCheck[j] = table.slotToCard[i];
                        j++;
                    }
                }
                if (cardsToCheck[2] != 0 && env.util.testSet(cardsToCheck)) {
                    players[playerId].point();
                    winningSet = cardsToCheck;
                    updateTimerDisplay(true);
                } else {
                    players[playerId].penalty();
                }
                table.shouldWait[playerId] = false;
                playerShouldWait[playerId].notifyAll();
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } else if (reshuffleTime - System.currentTimeMillis() > 0) {
            long currentTimeLeft = reshuffleTime - System.currentTimeMillis();
            env.ui.setCountdown(currentTimeLeft, currentTimeLeft <= env.config.turnTimeoutWarningMillis);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        synchronized (table) {
            table.switchingCards = true;
            for (int i = 0; i < env.config.tableSize; i++) {
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
        for (Player player : players) {
            if (player.score() > max) {
                max = player.score();
                winners.clear();
                winners.add(player.id);
            } else if (player.score() == max) {
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
        terminate();
    }
}
