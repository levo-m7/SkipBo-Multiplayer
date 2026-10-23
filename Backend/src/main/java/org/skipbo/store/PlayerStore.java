package org.skipbo.store;

import org.skipbo.model.Player;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStore
{
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    public void save(Player player)
    {
        players.put(player.getId(), player);
    }

    public Player findById(String playerId)
    {
        return players.get(playerId);
    }
}
