package org.skipbo.dto.websocket.game;

import java.util.List;

public record GameLocalPlayer(String playerId, int playerNumber, String username, int avatar, List<GameCard> hand, GameCard stockTopCard, int stockCount, List<List<GameCard>> discardPiles)
{
}
