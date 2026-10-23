package org.skipbo.model.card;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class DeckFactory
{
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<Card> MASTER_DECK = createMasterDeck();

    private DeckFactory() {}

    private static List<Card> createMasterDeck()
    {
        List<Card> cards = new ArrayList<>();
        int id = 1;

        for (int value = 1; value <= 12; value++)
        {
            for (int copy = 0; copy < 12; copy++)
            {
                cards.add(new Card(id, CardType.NUMBER, value));
                id++;
            }
        }

        for (int copy = 0; copy < 18; copy++)
        {
            cards.add(new Card(id, CardType.WILD, 0));
            id++;
        }
        return List.copyOf(cards);
    }

    public static Deque<Card> createShuffledDeck()
    {
        List<Card> cards = new ArrayList<>(MASTER_DECK);
        Collections.shuffle(cards, RANDOM);
        return new ArrayDeque<>(cards);
    }
}
