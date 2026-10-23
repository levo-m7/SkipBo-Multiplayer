package org.skipbo;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.skipbo.handler.GlobalRoomWebSocketHandler;
import org.skipbo.handler.RoomHandler;
import org.skipbo.handler.SessionHandler;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.service.RoomService;
import org.skipbo.service.SessionService;
import org.skipbo.store.PlayerStore;
import org.skipbo.store.RoomStore;
import org.skipbo.websocket.GlobalRoomConnections;
import org.skipbo.websocket.RoomConnections;

import java.time.Duration;

public class Main
{
    public static void main(String[] args)
    {
        PlayerStore playerStore = new PlayerStore();
        RoomStore roomStore = new RoomStore();

        SessionService sessionService = new SessionService(playerStore);

        RoomConnections roomConnections = new RoomConnections();
        GlobalRoomConnections globalRoomConnections = new GlobalRoomConnections();
        RoomService roomService = new RoomService(roomStore, playerStore, roomConnections, globalRoomConnections);

        SessionHandler sessionHandler = new SessionHandler(sessionService, roomService);

        RoomHandler roomHandler = new RoomHandler(roomService, sessionService);

        WebSocketHandler webSocketHandler = new WebSocketHandler(roomService, sessionService);

        GlobalRoomWebSocketHandler globalRoomWebSocketHandler = new GlobalRoomWebSocketHandler(roomService, globalRoomConnections);

        Javalin app = Javalin.create(config ->
        {
            config.jsonMapper(createJsonMapper());
            config.bundledPlugins.enableDevLogging();
            config.jetty.modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(Duration.ofMinutes(15)));
        });

        app.get("/api/me", sessionHandler::getPlayer);

        app.post("/api/me/updateUsername", sessionHandler::updateUsername);
        app.post("/api/me/updateAvatar", sessionHandler::updateAvatar);

        app.post("/api/rooms", roomHandler::createRoom);

        app.ws("/ws/rooms/{roomId}", ws ->
        {
            ws.onConnect(webSocketHandler::onConnect);
            ws.onMessage(webSocketHandler::onMessage);
            ws.onClose(webSocketHandler::onClose);
            ws.onError(webSocketHandler::onError);
        });

        app.ws("/ws/global/rooms", ws ->
        {
            ws.onConnect(globalRoomWebSocketHandler::onConnect);
            ws.onClose(globalRoomWebSocketHandler::onClose);
            ws.onError(globalRoomWebSocketHandler::onError);
        });

        app.events(events -> events.serverStopped(roomService::close));
        app.start(7070);
    }

    static JavalinJackson createJsonMapper()
    {
        JavalinJackson jsonMapper = new JavalinJackson();
        jsonMapper.getMapper().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        jsonMapper.getMapper().coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        jsonMapper.getMapper().coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        jsonMapper.getMapper().coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        jsonMapper.getMapper().coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return jsonMapper;
    }
}
