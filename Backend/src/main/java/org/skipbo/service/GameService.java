package org.skipbo.service;

import org.skipbo.model.card.Card;
import org.skipbo.model.card.CardType;
import org.skipbo.model.card.DeckFactory;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class GameService
{
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 6;

    private static final int NORMAL_STOCK_SIZE = 30;
    private static final int LARGE_GAME_STOCK_SIZE = 20;

    private static final int HAND_SIZE = 5;

    public GameState createGame(List<String> playerIds)
    {
        if (playerIds.size() < MIN_PLAYERS || playerIds.size() > MAX_PLAYERS)
        {
            throw new IllegalArgumentException("Game must have between 2 and 6 players");
        }

        GameState gameState = new GameState(playerIds);
        Deque<Card> deck = DeckFactory.createShuffledDeck();

        int stockPileSize = NORMAL_STOCK_SIZE;

        if (playerIds.size() >= 5)
        {
            stockPileSize = LARGE_GAME_STOCK_SIZE;
        }

        for (int card = 0; card < stockPileSize; card++)
        {
            for (GamePlayerState player : gameState.getPlayers())
            {
                player.getStockPile().add(deck.removeFirst());
            }
        }

        gameState.getDrawPile().addAll(deck);

        gameState.setCurrentPlayerId(playerIds.get(0));

        return gameState;
    }

    public void drawCardsToFive(GameState gameState, GamePlayerState player)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return;
        }

        while (player.getHand().size() < HAND_SIZE)
        {
            if (gameState.getDrawPile().isEmpty())
            {
                if (gameState.getCompletedCards().isEmpty())
                {
                    return;
                }

                gameState.getDrawPile().addAll(gameState.getCompletedCards());
                Collections.shuffle(gameState.getDrawPile(), RANDOM);
                gameState.getCompletedCards().clear();
            }

            Card card = gameState.getDrawPile().remove(0);

            player.getHand().add(card);
        }
    }

    public GamePlayerState findGamePlayer(GameState gameState, String playerId)
    {
        for (GamePlayerState player : gameState.getPlayers())
        {
            if (player.getPlayerId().equals(playerId))
            {
                return player;
            }
        }

        return null;
    }

    public void startTurn(GameState gameState)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return;
        }

        String currentPlayerId = gameState.getCurrentPlayerId();

        GamePlayerState currentPlayer = findGamePlayer(gameState, currentPlayerId);

        if (currentPlayer == null)
        {
            return;
        }

        drawCardsToFive(gameState, currentPlayer);
    }

    public void nextTurn(GameState gameState)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return;
        }

        List<GamePlayerState> players = gameState.getPlayers();

        if (players.isEmpty())
        {
            return;
        }

        String currentPlayerId = gameState.getCurrentPlayerId();

        int currentPlayerIndex = -1;

        for (int i = 0; i < players.size(); i++)
        {
            if (players.get(i).getPlayerId().equals(currentPlayerId))
            {
                currentPlayerIndex = i;
                break;
            }
        }

        if (currentPlayerIndex == -1)
        {
            return;
        }

        int nextPlayerIndex = currentPlayerIndex + 1;

        if (nextPlayerIndex >= players.size())
        {
            nextPlayerIndex = 0;
        }

        GamePlayerState nextPlayer = players.get(nextPlayerIndex);

        gameState.setCurrentPlayerId(nextPlayer.getPlayerId());

        startTurn(gameState);
    }

    public Card getTopCard(List<Card> pile)
    {
        if (pile.isEmpty())
        {
            return null;
        }

        return pile.get(0);
    }

    public boolean canPlayOnBuildingPile(Card card, List<Card> buildingPile)
    {
        if (card == null)
        {
            return false;
        }

        if (buildingPile.size() >= 12)
        {
            return false;
        }

        if (card.type() == CardType.WILD)
        {
            return true;
        }

        int requiredValue = buildingPile.size() + 1;

        return card.value() == requiredValue;
    }

    public boolean placeCardOnBuildingPile(Card card, List<Card> buildingPile)
    {
        if (!canPlayOnBuildingPile(card, buildingPile))
        {
            return false;
        }

        buildingPile.add(0, card);

        return true;
    }

    public boolean playCardFromHand(GameState gameState, String playerId, int cardId, int buildingPileIndex)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return false;
        }

        if (playerId == null)
        {
            return false;
        }

        if (!playerId.equals(gameState.getCurrentPlayerId()))
        {
            return false;
        }

        GamePlayerState player = findGamePlayer(gameState, playerId);

        if (player == null)
        {
            return false;
        }

        if (buildingPileIndex < 0 || buildingPileIndex >= gameState.getBuildingPiles().size())
        {
            return false;
        }

        Card card = null;

        for (Card handCard : player.getHand())
        {
            if (handCard.id() == cardId)
            {
                card = handCard;
                break;
            }
        }

        if (card == null)
        {
            return false;
        }

        List<Card> buildingPile = gameState.getBuildingPiles().get(buildingPileIndex);

        if (!canPlayOnBuildingPile(card, buildingPile))
        {
            return false;
        }

        player.getHand().remove(card);

        buildingPile.add(0, card);
        recycleBuildingPileIfComplete(gameState, buildingPile);

        if (player.getHand().isEmpty())
        {
            drawCardsToFive(gameState, player);
        }

        return true;

    }

    public boolean playCardFromStock(GameState gameState, String playerId, int buildingPileIndex)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return false;
        }

        if (playerId == null)
        {
            return false;
        }

        if (!playerId.equals(gameState.getCurrentPlayerId()))
        {
            return false;
        }

        GamePlayerState player = findGamePlayer(gameState, playerId);

        if (player == null)
        {
            return false;
        }

        if (player.getStockPile().isEmpty())
        {
            return false;
        }

        if (buildingPileIndex < 0 || buildingPileIndex >= gameState.getBuildingPiles().size())
        {
            return false;
        }

        Card card = getTopCard(player.getStockPile());

        List<Card> buildingPile = gameState.getBuildingPiles().get(buildingPileIndex);

        if (!canPlayOnBuildingPile(card, buildingPile))
        {
            return false;
        }

        player.getStockPile().remove(0);

        buildingPile.add(0, card);
        recycleBuildingPileIfComplete(gameState, buildingPile);

        if (player.getStockPile().isEmpty())
        {
            gameState.setWinnerPlayerId(playerId);
        }

        return true;
    }

    public boolean playCardFromDiscard(GameState gameState, String playerId, int discardPileIndex, int buildingPileIndex)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return false;
        }

        if (playerId == null)
        {
            return false;
        }

        if (!playerId.equals(gameState.getCurrentPlayerId()))
        {
            return false;
        }

        GamePlayerState player = findGamePlayer(gameState, playerId);

        if (player == null)
        {
            return false;
        }

        if (discardPileIndex < 0 || discardPileIndex >= player.getDiscardPiles().size())
        {
            return false;
        }

        if (buildingPileIndex < 0 || buildingPileIndex >= gameState.getBuildingPiles().size())
        {
            return false;
        }

        List<Card> discardPile = player.getDiscardPiles().get(discardPileIndex);

        if (discardPile.isEmpty())
        {
            return false;
        }

        Card card = getTopCard(discardPile);

        List<Card> buildingPile = gameState.getBuildingPiles().get(buildingPileIndex);

        if (!canPlayOnBuildingPile(card, buildingPile))
        {
            return false;
        }

        discardPile.remove(0);

        buildingPile.add(0, card);
        recycleBuildingPileIfComplete(gameState, buildingPile);

        return true;
    }

    public boolean discardCardFromHand(GameState gameState, String playerId, int cardId, int discardPileIndex)
    {
        if (gameState.getWinnerPlayerId() != null)
        {
            return false;
        }

        if (playerId == null || !playerId.equals(gameState.getCurrentPlayerId()))
        {
            return false;
        }

        GamePlayerState player = findGamePlayer(gameState, playerId);

        if (player == null)
        {
            return false;
        }

        if (discardPileIndex < 0 || discardPileIndex >= player.getDiscardPiles().size())
        {
            return false;
        }

        Card card = null;

        for (Card handCard : player.getHand())
        {
            if (handCard.id() == cardId)
            {
                card = handCard;
                break;
            }
        }

        if (card == null)
        {
            return false;
        }

        List<Card> discardPile = player.getDiscardPiles().get(discardPileIndex);
        player.getHand().remove(card);
        discardPile.add(0, card);
        nextTurn(gameState);

        return true;
    }

    public void recycleBuildingPileIfComplete(GameState gameState, List<Card> buildingPile)
    {
        if (buildingPile.size() == 12)
        {
            gameState.getCompletedCards().addAll(buildingPile);
            buildingPile.clear();
        }
    }
}
