package org.skipbo.handler;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.skipbo.dto.AvatarRequest;
import org.skipbo.dto.UsernameRequest;
import org.skipbo.model.Player;
import org.skipbo.service.RoomService;
import org.skipbo.service.SessionService;

public class SessionHandler
{
    private final SessionService sessionService;
    private final RoomService roomService;

    public SessionHandler(SessionService sessionService, RoomService roomService)
    {
        this.sessionService = sessionService;
        this.roomService = roomService;
    }

    public void getPlayer(Context ctx)
    {
        Player player = sessionService.getPlayer(ctx);

        if (player == null)
        {
            player = sessionService.createPlayer(ctx);
        }

        ctx.json(player);
    }

    public void updateUsername(Context ctx)
    {
        try
        {
            UsernameRequest request = ctx.bodyAsClass(UsernameRequest.class);

            if (request == null)
            {
                throw new IllegalArgumentException("Missing username");
            }

            Player player = sessionService.getPlayer(ctx);

            if (player == null)
            {
                player = sessionService.createPlayer(ctx);
            }

            roomService.updateUsername(player, request.username());
            ctx.json(player);
        }
        catch (BadRequestResponse | IllegalArgumentException exception)
        {
            ctx.status(400).result("Invalid username request");
        }
        catch (IllegalStateException exception)
        {
            ctx.status(409).result(exception.getMessage());
        }
    }

    public void updateAvatar(Context ctx)
    {
        try
        {
            AvatarRequest request = ctx.bodyAsClass(AvatarRequest.class);

            if (request == null)
            {
                throw new IllegalArgumentException("Missing avatar");
            }

            Player player = sessionService.getPlayer(ctx);

            if (player == null)
            {
                player = sessionService.createPlayer(ctx);
            }

            roomService.updateAvatar(player, request.avatar());
            ctx.json(player);
        }
        catch (BadRequestResponse | IllegalArgumentException exception)
        {
            ctx.status(400).result("Invalid avatar request");
        }
        catch (IllegalStateException exception)
        {
            ctx.status(409).result(exception.getMessage());
        }
    }
}
