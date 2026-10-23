package org.skipbo.dto.websocket;

public record LobbyPlayer(String id, String username, int avatar, boolean ready, boolean host)
{
}
