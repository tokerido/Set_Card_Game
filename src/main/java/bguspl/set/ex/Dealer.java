package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
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
    private long reshuffleTime;
    private long currentTimeLeft;
    private long timeToWait;
    public final Object actionLocker;
    public final Object setLocker;
    public final Object[] playerShouldWait;
    private final boolean noTimeMode;
    private boolean noSetsLeft;
    public final long secInMil = 1000;
    public final long tenMil = 10;
    public final long oneMil = 1;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        actionLocker = new Object();
        setLocker = new Object();
        playerShouldWait = new Object[env.config.players];
        Arrays.fill(playerShouldWait, new Object());
        reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
        currentTimeLeft = env.config.turnTimeoutMillis;
        noTimeMode = env.config.turnTimeoutMillis <= 0;
        timeToWait = secInMil;
        noSetsLeft = false;
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
        while (!terminate && (noTimeMode || System.currentTimeMillis() < reshuffleTime)) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
            if (noSetsLeft) {
                break;
            }
        }
        noSetsLeft = false;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        try {
            for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
                players[i].getThread().interrupt();
                players[i].getThread().join();
            }
            Thread.currentThread().interrupt();
        } catch (InterruptedException ignored) {
        }
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
        Integer playerId = table.setAnnouncements.poll();
        if (playerId != null) {
            int[] cardsToCheck = new int[env.config.featureSize];
            boolean needToRemove = false;
            synchronized (playerShouldWait[playerId]) {
                int j = 0;
                for (int i = 0; i < env.config.tableSize; i++) {
                    if (table.playersToTokens[playerId][i] == 1) {
                        cardsToCheck[j] = table.slotToCard[i];
                        j++;
                    }
                }
                if (j == env.config.featureSize && env.util.testSet(cardsToCheck)) {
                    players[playerId].point();
                    updateTimerDisplay(true);
                    needToRemove = true;
                } else {
                    players[playerId].penalty();
                }
                table.shouldWait[playerId] = false;
                playerShouldWait[playerId].notifyAll();
            }
            if (needToRemove) {
                for (int i = 0; i < cardsToCheck.length; i++) {
                    table.removeCard(table.cardToSlot[cardsToCheck[i]]);
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        Collections.shuffle(deck);
        int changed = 0;
        for (int i = 0; i < env.config.tableSize; i++) {
            int rndCard = (int) (Math.random() * deck.size());
            if (table.slotToCard[i] == null && !deck.isEmpty()) {
                int card = deck.remove(rndCard);
                table.placeCard(card, i);
                changed += 1;
            }
        }
        table.switchingCards = false;
        synchronized (actionLocker) {
            actionLocker.notifyAll();
        }
        if (changed != 0) {
            updateTimerDisplay(true);
        }
        if (noTimeMode) {
            List<Integer> cardsOnTable = Arrays.asList(table.slotToCard);
            if (cardsOnTable.contains(null)) {
                cardsOnTable = new ArrayList<>();
                for (Integer card : table.slotToCard) {
                    if (card != null) {
                        cardsOnTable.add(card);
                    }
                }
            }
            noSetsLeft = env.util.findSets(cardsOnTable, 1).isEmpty();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        if (!noTimeMode) {
            synchronized (setLocker) {
                try {
                    long runTime = currentTimeLeft - reshuffleTime + System.currentTimeMillis();
                    if (table.setAnnouncements.isEmpty() && timeToWait - runTime > oneMil) {
                        setLocker.wait(timeToWait - runTime - oneMil);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        } else if (env.config.turnTimeoutMillis < 0) {
            synchronized (setLocker) {
                try {
                    if (table.setAnnouncements.isEmpty()) {
                        setLocker.wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        } else {
            synchronized (setLocker) {
                try {
                    long runTime = System.currentTimeMillis() - currentTimeLeft - reshuffleTime;
                    if (table.setAnnouncements.isEmpty() && timeToWait - runTime > oneMil) {
                        setLocker.wait(timeToWait - runTime - oneMil);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (!noTimeMode) {
            currentTimeLeft = reshuffleTime - System.currentTimeMillis();
            if (reset) {
                reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis();
                currentTimeLeft = env.config.turnTimeoutMillis;
                env.ui.setCountdown(env.config.turnTimeoutMillis, env.config.turnTimeoutMillis <= env.config.turnTimeoutWarningMillis);
            } else if (currentTimeLeft > 0) {
                env.ui.setCountdown(currentTimeLeft, currentTimeLeft <= env.config.turnTimeoutWarningMillis);
                if (currentTimeLeft > env.config.turnTimeoutWarningMillis) {
                    timeToWait = secInMil;
                } else {
                    timeToWait = Math.min(currentTimeLeft, tenMil);
                }
            }
        } else if (env.config.turnTimeoutMillis == 0) {
            if (reset) {
                reshuffleTime = System.currentTimeMillis();
                env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            } else {
                currentTimeLeft = System.currentTimeMillis() - reshuffleTime;
                env.ui.setCountdown(currentTimeLeft, false);
            }
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
