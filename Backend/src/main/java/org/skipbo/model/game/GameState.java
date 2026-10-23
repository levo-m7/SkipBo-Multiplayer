package org.skipbo.model.game;

import org.skipbo.model.card.Card;

import java.util.ArrayList;
import java.util.List;

public class GameState
{
    private static final int BUILDING_PILE_COUNT = 4;

    private final List<GamePlayerState> players;
    private final List<Card> drawPile;
    private final List<List<Card>> buildingPiles;
    private final List<Card> completedCards;

    private String currentPlayerId;
    private String winnerPlayerId;

    public GameState(List<String> playerIds)
    {
        this.players = new ArrayList<>();
        this.drawPile = new ArrayList<>();
        this.buildingPiles = new ArrayList<>();
        this.completedCards = new ArrayList<>();

        for(String playerId: playerIds)
        {
            players.add(new GamePlayerState(playerId));
        }

        for(int i = 0; i < BUILDING_PILE_COUNT; i++)
        {
            buildingPiles.add(new ArrayList<>());
        }
    }

    public List<GamePlayerState> getPlayers()
    {
        return players;
    }

    public List<Card> getDrawPile()
    {
        return drawPile;
    }

    public List<List<Card>> getBuildingPiles()
    {
        return buildingPiles;
    }

    public List<Card> getCompletedCards()
    {
        return completedCards;
    }

    public String getCurrentPlayerId()
    {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId)
    {
        this.currentPlayerId = currentPlayerId;
    }

    public String getWinnerPlayerId()
    {
        return winnerPlayerId;
    }

    public void setWinnerPlayerId(String winnerPlayerId)
    {
        this.winnerPlayerId = winnerPlayerId;
    }

}
