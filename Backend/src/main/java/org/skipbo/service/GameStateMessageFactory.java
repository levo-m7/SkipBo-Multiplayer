package org.skipbo.service;

import org.skipbo.dto.websocket.game.GameBuildingPile;
import org.skipbo.dto.websocket.game.GameCard;
import org.skipbo.dto.websocket.game.GameLocalPlayer;
import org.skipbo.dto.websocket.game.GameOpponent;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.model.Player;
import org.skipbo.model.card.Card;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.store.PlayerStore;

import java.util.ArrayList;
import java.util.List;

public class GameStateMessageFactory
{
    private final PlayerStore playerStore;

    public GameStateMessageFactory(PlayerStore playerStore)
    {
        this.playerStore = playerStore;
    }

    public GameStateMessage createGameStateMessage(Room room, String viewerPlayerId)
    {
        if (room == null || viewerPlayerId == null)
        {
            throw new IllegalArgumentException("Room and viewer are required");
        }

        synchronized (room)
        {
            GameState gameState = room.getGameState();

            if (gameState == null)
            {
                throw new IllegalStateException("Room has no game");
            }

            if (!room.hasPlayer(viewerPlayerId))
            {
                throw new IllegalArgumentException("Viewer is not in this room");
            }

            GamePlayerState viewer = null;
            int viewerNumber = 0;

            for (int i = 0; i < gameState.getPlayers().size(); i++)
            {
                GamePlayerState player = gameState.getPlayers().get(i);

                if (player.getPlayerId().equals(viewerPlayerId))
                {
                    viewer = player;
                    viewerNumber = i + 1;
                    break;
                }
            }

            if (viewer == null)
            {
                throw new IllegalArgumentException("Viewer is not in this game");
            }

            GameLocalPlayer localPlayer = createLocalPlayer(viewer, viewerNumber);
            List<GameOpponent> opponents = new ArrayList<>();

            for (int i = 0; i < gameState.getPlayers().size(); i++)
            {
                GamePlayerState player = gameState.getPlayers().get(i);

                if (!player.getPlayerId().equals(viewerPlayerId))
                {
                    opponents.add(createOpponent(player, i + 1));
                }
            }

            List<GameBuildingPile> buildingPiles = new ArrayList<>();

            for (List<Card> buildingPile : gameState.getBuildingPiles())
            {
                buildingPiles.add(new GameBuildingPile(createTopCard(buildingPile), buildingPile.size()));
            }

            return new GameStateMessage("GAME_STATE", room.getId(), gameState.getPlayers().size(), gameState.getCurrentPlayerId(), gameState.getWinnerPlayerId(), gameState.getDrawPile().size(), buildingPiles, localPlayer, opponents);
        }
    }

    private GameLocalPlayer createLocalPlayer(GamePlayerState player, int playerNumber)
    {
        Player profile = requireProfile(player.getPlayerId());
        List<GameCard> hand = createVisibleCards(player.getHand());
        List<List<GameCard>> discardPiles = new ArrayList<>();

        for (List<Card> discardPile : player.getDiscardPiles())
        {
            discardPiles.add(createVisibleCards(discardPile));
        }

        return new GameLocalPlayer(player.getPlayerId(), playerNumber, profile.getUsername(), profile.getAvatar(), hand, createTopCard(player.getStockPile()), player.getStockPile().size(), discardPiles);
    }

    private GameOpponent createOpponent(GamePlayerState player, int playerNumber)
    {
        Player profile = requireProfile(player.getPlayerId());
        List<GameCard> discardTopCards = new ArrayList<>();

        for (List<Card> discardPile : player.getDiscardPiles())
        {
            discardTopCards.add(createTopCard(discardPile));
        }

        return new GameOpponent(player.getPlayerId(), playerNumber, profile.getUsername(), profile.getAvatar(), player.getHand().size(), createTopCard(player.getStockPile()), player.getStockPile().size(), discardTopCards);
    }

    private Player requireProfile(String playerId)
    {
        Player player = playerStore.findById(playerId);

        if (player == null)
        {
            throw new IllegalStateException("Game player profile not found");
        }

        return player;
    }

    private GameCard createTopCard(List<Card> pile)
    {
        if (pile.isEmpty())
        {
            return null;
        }

        return createCard(pile.get(0));
    }

    private List<GameCard> createVisibleCards(List<Card> cards)
    {
        List<GameCard> visibleCards = new ArrayList<>();

        for (Card card : cards)
        {
            visibleCards.add(createCard(card));
        }

        return visibleCards;
    }

    private GameCard createCard(Card card)
    {
        return new GameCard(card.id(), card.type(), card.value());
    }
}
