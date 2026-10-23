package org.skipbo.dto.websocket.game;

import java.util.List;

public record GameOpponent(String playerId, int playerNumber, String username, int avatar, int handCount, GameCard stockTopCard, int stockCount, List<GameCard> discardTopCards)
{
}
