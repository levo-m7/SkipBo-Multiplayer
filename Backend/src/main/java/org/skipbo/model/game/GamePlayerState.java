package org.skipbo.model.game;

import org.skipbo.model.card.Card;

import java.util.ArrayList;
import java.util.List;

public class GamePlayerState
{
    private static final int DISCARD_PILE_COUNT = 4;

    private final String playerId;
    private final List<Card> hand;
    private final List<Card> stockPile;
    private final List<List<Card>> discardPiles;

    public GamePlayerState(String playerId)
    {
        this.playerId = playerId;
        this.hand = new ArrayList<>();
        this.stockPile = new ArrayList<>();
        this.discardPiles = new ArrayList<>();

        for(int i = 0; i  < DISCARD_PILE_COUNT; i++)
        {
            discardPiles.add(new ArrayList<>());
        }
    }

    public String getPlayerId()
    {
        return playerId;
    }

    public List<Card> getHand()
    {
        return hand;
}

    public List<Card> getStockPile()
    {
        return stockPile;
    }

    public List<List<Card>> getDiscardPiles()
    {
        return discardPiles;
    }


}
