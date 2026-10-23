package org.skipbo.handler;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.skipbo.dto.room.CreateRoomRequest;
import org.skipbo.dto.room.CreateRoomResponse;
import org.skipbo.model.Player;
import org.skipbo.model.room.Room;
import org.skipbo.service.RoomService;
import org.skipbo.service.SessionService;

public class RoomHandler
{
    private final RoomService roomService;
    private final SessionService sessionService;

    public RoomHandler(RoomService roomService, SessionService sessionService)
    {
        this.roomService = roomService;
        this.sessionService = sessionService;
    }

    public void createRoom(Context ctx)
    {
        try
        {
            CreateRoomRequest request = ctx.bodyAsClass(CreateRoomRequest.class);
            Player player = sessionService.getPlayer(ctx);

            if (player == null)
            {
                player = sessionService.createPlayer(ctx);
            }

            Room room = roomService.createRoom(player.getId(), request);
            CreateRoomResponse response = new CreateRoomResponse(room.getId(), room.getId(), room.getId().substring(0, 8), room.getName(), room.getPlayerTarget());
            ctx.status(201).json(response);
        }
        catch (BadRequestResponse | IllegalArgumentException exception)
        {
            ctx.status(400).result("Invalid room request");
        }
        catch (IllegalStateException exception)
        {
            ctx.status(409).result(exception.getMessage());
        }
    }
}
