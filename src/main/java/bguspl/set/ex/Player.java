package bguspl.set.ex;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    private final Dealer dealer; //dealer field

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //private Set<Integer> myCards; //new field to hold players cards.
    private BlockingQueue<Integer> actions; //new field to hold the actions we need to do.
    private volatile AtomicLong timeToSleep;
    private final AtomicLong ZERO = new AtomicLong(0);


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        actions = new ArrayBlockingQueue<>(env.config.featureSize);
        timeToSleep = new AtomicLong(0);
    }


    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            try {
                if (!table.switchingCards) {
                    synchronized (dealer.playerShouldWait[id]) {
                        while (table.shouldWait[id]) {
                            dealer.playerShouldWait[id].wait();
                        }
                    }
                    if (timeToSleep.get() > ZERO.get()) {
                        playerSleep();
                    }

                    int slot = actions.take();

                    if (!table.removeToken(id, slot)) {
                        if (table.isTokenLegal(slot) && !table.playerHasSet(id)) {
                            table.placeToken(id, slot);
                        }
                        if (table.playerHasSet(id)) {
                            synchronized (dealer.setLocker) {
                                dealer.setLocker.notifyAll();
                            }
                        }

                    }
                } else {
                    synchronized (dealer.actionLocker) {
                        while (table.switchingCards) {
                            dealer.actionLocker.wait();
                        }
                        dealer.actionLocker.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                // TODO: handle exception
                System.out.println("Thred" + this.hashCode() + "has been interrupted");
            }


        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) {
                // TODO implement player key press simulator

                if (!table.switchingCards) {
                    if (table.playerHasSet(id)) {
                        for (int i = 0; i < table.playersToTokens[id].length; i++) {
                            if (table.playersToTokens[id][i] == 1) {
                                keyPressed(i);
                            }
                        }
                    }

                    if (actions.remainingCapacity() > 0) {
                        int rndCard = (int) (Math.random() * env.config.tableSize);
                        keyPressed(rndCard);
                    }

                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        if (!human) {
            aiThread.interrupt();
        }
        terminate = true;
    }

    protected Thread getThread() {
        return playerThread;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (!table.switchingCards && !table.shouldWait[id]) {
            try {
                actions.put(slot);
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score); //update score
        timeToSleep.compareAndSet(0, env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        //TODO implement
        timeToSleep.compareAndSet(0, env.config.penaltyFreezeMillis);
    }

    public void playerSleep() {
        try {
            long startingTime = System.currentTimeMillis();
            while (timeToSleep.get() > ZERO.get()) {
                env.ui.setFreeze(id, timeToSleep.get());
                Thread.sleep(300);
                timeToSleep.compareAndSet(timeToSleep.get(), timeToSleep.get() + startingTime - System.currentTimeMillis());
            }
            actions.clear();
            env.ui.setFreeze(id, 0);
        } catch (Exception e) {
            // TODO: handle exception
        }
        timeToSleep.set(ZERO.get());
    }

    public int score() {
        return score;
    }
}
