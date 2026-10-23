package org.skipbo.dto.room;

public record ChatMessage(String id,String playerId, String username, int avatar, String message)
{
}
