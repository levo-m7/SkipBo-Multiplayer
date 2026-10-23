package org.skipbo.dto.room;

public record CreateRoomResponse(String id, String roomId, String roomCode, String roomName, int playerTarget)
{
}
