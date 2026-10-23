package org.skipbo.dto.websocket;

import org.skipbo.dto.room.ChatMessage;
import java.util.List;

public record RoomStateMessage(String type, String roomId, String roomCode, String roomName, int playerTarget, String state, List<LobbyPlayer> players, List<ChatMessage> chatMessages)
{
}
