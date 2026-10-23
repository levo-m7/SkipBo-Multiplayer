package org.skipbo.dto.websocket;

public record RoomSummary(String roomId, String roomCode, String roomName, int playerCount, int playerTarget, String state, boolean full)
{
}