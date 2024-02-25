package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    protected final Integer[][] playersToTokens; // new field

    protected volatile boolean switchingCards; //when placing cards

    public Semaphore fairSemaphore;

    protected BlockingQueue<Integer> setAnnouncements;

    protected volatile boolean[] shouldWait;


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     * //@param playersToTokens - mapping between a player and the slot he picked (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.playersToTokens = new Integer[env.config.players][env.config.tableSize];
        for(int i = 0; i < env.config.players; i++) {
            Arrays.fill(playersToTokens[i], 0);
        }
        switchingCards = true;
        fairSemaphore = new Semaphore(1,true);
        setAnnouncements = new ArrayBlockingQueue<>(env.config.players, true);
        shouldWait = new boolean[env.config.players];
        Arrays.fill(shouldWait, false);
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public synchronized void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        // TODO implement
        //our code start here
        if(slotToCard[slot] == null) {
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot); 
        }
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public synchronized void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        // TODO implement
        //our code start here
        if(slotToCard != null) {
            int card = slotToCard[slot];
            for (int i = 0; i < env.config.players; i++) {
                removeToken(i, slot);
            }
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
        }
    }
    /**
     * checks if a player can place the Token
     * @param slot   - the slot on which to place the token.
     */
     public boolean isTokenLegal(int slot) {
        return slotToCard[slot] != null;
     }
    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public synchronized void placeToken(int player, int slot) {
        // TODO implement
        if(isTokenLegal(slot) && (!playerHasSet(player)) )
        {
            try {
                playersToTokens[player][slot] = 1;
                env.ui.placeToken(player, slot);
                if (playerHasSet(player)) {
                    setAnnouncements.put(player);
                    shouldWait[player] = true;
                }
            } catch(InterruptedException ignored) {}

        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public synchronized boolean removeToken(int player, int slot) {
        // TODO implement
        if(slotToCard[slot] != null && playersToTokens[player][slot] == 1)
        {
            playersToTokens[player][slot] = 0;
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }

    public synchronized boolean playerHasSet(int player) {
        Integer sum = 0;
        for (Integer i : playersToTokens[player]) {
            sum += i;
        }
        return sum == env.config.featureSize;
    }
}
