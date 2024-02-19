package bguspl.set.ex;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicIntegerArray;

import javax.swing.text.html.HTMLDocument.Iterator;

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

    private Set<Integer> myCards; //new field to hold players cards. 
    private Queue<Integer> actions; //new field to hold the actions we need to do.



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
        myCards = new ConcurrentSkipListSet<>();
        actions = new ConcurrentLinkedQueue<>();
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
                synchronized(actions) {
                    while (actions.isEmpty()) {
                        actions.wait();
                    }
                    int slot = actions.poll();
                    if (myCards.remove(slot)) {
                        table.removeToken(id, slot);
                    } else if (myCards.size() < 3) {
                        if (table.isTokenLegal(slot)) {
                            myCards.add(slot);
                            table.placeToken(id, slot);
                        }
                    }
                    if(myCards.size() == 3) {
                        dealer.getStetFromPlayer(id, getSet());
                    }

//                    playerThread.notifyAll();
            }
            } catch (InterruptedException e) {
               // TODO: handle exception
            }
            
            
            
         }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
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
            try {
                Thread.sleep(5000);  
            } catch (InterruptedException ignored) {
                // TODO: handle exception
            }
            
            while (!terminate) {
                // TODO implement player key press simulator
                if (!human) {
                    for (Integer slot : myCards) {
                        keyPressed(slot);
                    }
                }
                for (int i = 0 ; i < 3 ; i++) {
                    int rndCard = (int)(Math.random() * 12);
                    keyPressed(rndCard);
                }

              //  try {
              //      synchronized (this) { wait(); }
              //  } catch (InterruptedException ignored) {}
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
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (!table.switchingCards) {
         try {
            synchronized (actions){
                if (actions.size() < 3) {
                    actions.add(slot);
                }
                actions.notifyAll();
            }
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
        myCards.clear();
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score); //update score
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
         //TODO implement
        playerSleep(env.config.penaltyFreezeMillis);
    }

    public void playerSleep(long time) {
        try {
            long startingTime = System.currentTimeMillis();
            while (time > 0) {
                env.ui.setFreeze(id, time);
                Thread.sleep(time);
                time = time + startingTime - System.currentTimeMillis();
            }
            env.ui.setFreeze(id, 0);
            synchronized (actions) {
                actions.clear();
                actions.notifyAll();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public int score() {
        return score;
    }

    public int[] getSet() {
//        try{
//           synchronized (playerThread) {
               int[] slots = new int[3];
               int i = 0;
               for (int slot : myCards) {
                   slots[i] = slot;
                   i++;
               }
//               playerThread.notifyAll();
               return slots;
//           }
//        } catch (Exception ignored) {} //check this
//        return null;
    }
}
