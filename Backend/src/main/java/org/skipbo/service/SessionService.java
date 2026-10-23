package org.skipbo.service;

import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import org.skipbo.model.Player;
import org.skipbo.store.PlayerStore;
import java.util.UUID;

public class SessionService
{
    private static final String PLAYER_ID_SESSION_KEY = "playerID";
    private final PlayerStore playerStore;

    public SessionService(PlayerStore playerStore)
    {
        this.playerStore = playerStore;
    }

    public Player getPlayer(Context ctx)
    {
        String playerId = ctx.sessionAttribute(PLAYER_ID_SESSION_KEY);

        if (playerId == null)
        {
            return null;
        }

        return playerStore.findById(playerId);
    }

    public Player getPlayer(WsContext ctx)
    {
        String playerId = ctx.sessionAttribute(PLAYER_ID_SESSION_KEY);

        if (playerId == null)
        {
            return null;
        }

        return playerStore.findById(playerId);
    }

    public Player createPlayer(Context ctx)
    {
        synchronized (ctx.req().getSession())
        {
            Player existingPlayer = getPlayer(ctx);

            if (existingPlayer != null)
            {
                return existingPlayer;
            }

            String playerId = UUID.randomUUID().toString();
            Player player = new Player(playerId);
            playerStore.save(player);
            ctx.sessionAttribute(PLAYER_ID_SESSION_KEY, playerId);
            return player;
        }
    }
}
