package org.skipbo.dto.websocket.game;

import java.util.List;

public record GameStateMessage(String type, String roomId, int playerCount, String currentPlayerId, String winnerPlayerId, int drawPileCount, List<GameBuildingPile> buildingPiles, GameLocalPlayer localPlayer, List<GameOpponent> opponents)
{
}
