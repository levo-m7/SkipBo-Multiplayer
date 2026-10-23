package org.skipbo.dto.websocket;

public record RoomClientMessage(String type, String username, Integer avatar, String message, Integer cardId, Integer buildingPileIndex, Integer discardPileIndex)
{
}
