package org.skipbo;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.skipbo.dto.room.CreateRoomRequest;
import org.skipbo.model.Player;
import org.skipbo.model.room.Room;
import org.skipbo.service.RoomService;
import org.skipbo.service.SessionService;
import org.skipbo.store.PlayerStore;
import org.skipbo.store.RoomStore;
import org.skipbo.websocket.GlobalRoomConnections;
import org.skipbo.websocket.RoomConnections;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class LobbyTestSupport
{
    static final JavalinJackson JSON = Main.createJsonMapper();

    static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    static void expectRejected(Runnable action)
    {
        try
        {
            action.run();
        }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            return;
        }

        throw new AssertionError("Expected request to be rejected");
    }

    static class TestScheduler extends ScheduledThreadPoolExecutor
    {
        private final List<Runnable> tasks = new ArrayList<>();

        TestScheduler()
        {
            super(1);
        }

        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
        {
            check(unit.toMillis(delay) == 15000, "Production grace should be 15 seconds");
            tasks.add(command);
            return super.schedule(command, 1, TimeUnit.DAYS);
        }

        void expireAll()
        {
            List<Runnable> pendingTasks = new ArrayList<>(tasks);
            tasks.clear();

            // Run even canceled callbacks to simulate a timeout already waiting for the service lock.
            for (Runnable task : pendingTasks)
            {
                task.run();
            }
        }
    }

    static class Lobby implements AutoCloseable
    {
        final RoomStore rooms = new RoomStore();
        final PlayerStore players = new PlayerStore();
        final RoomConnections connections = new RoomConnections();
        final GlobalRoomConnections globalConnections = new GlobalRoomConnections();
        final TestScheduler scheduler = new TestScheduler();
        final RoomService service = new RoomService(rooms, players, connections, globalConnections, scheduler, 15000);
        final SessionService sessions = new SessionService(players);

        Player player(String id)
        {
            Player player = new Player(id);
            players.save(player);
            return player;
        }

        Room room(Player host, int target)
        {
            return service.createRoom(host.getId(), new CreateRoomRequest("Fun Room", target));
        }

        TestSocket connect(Player player, Room room) throws Exception
        {
            TestSocket socket = new TestSocket(player.getId(), room.getId());
            service.connectPlayer(player.getId(), room.getId(), socket.connect());
            return socket;
        }

        public void close()
        {
            service.close();
        }
    }

    static class TestHttp
    {
        int status = 200;
        Object response;
        String result;
        final Context context;

        TestHttp(String playerId, String body)
        {
            context = (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class[]{Context.class}, (proxy, method, arguments) ->
            {
                switch (method.getName())
                {
                    case "sessionAttribute":
                        return playerId;
                    case "bodyAsClass":
                        try
                        {
                            return JSON.fromJsonString(body, (Class<?>) arguments[0]);
                        }
                        catch (Exception exception)
                        {
                            throw new BadRequestResponse("Invalid JSON");
                        }
                    case "status":
                        status = (Integer) arguments[0];
                        return proxy;
                    case "json":
                        response = arguments[0];
                        return proxy;
                    case "result":
                        result = (String) arguments[0];
                        return proxy;
                    default:
                        return null;
                }
            });
        }
    }

    static class TestSocket
    {
        final List<String> messages = new ArrayList<>();
        final Session session;
        final String playerId;
        final String roomId;
        boolean failSend;
        int closeCode;

        TestSocket(String playerId, String roomId)
        {
            this.playerId = playerId;
            this.roomId = roomId;
            RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(RemoteEndpoint.class.getClassLoader(), new Class[]{RemoteEndpoint.class}, (proxy, method, arguments) ->
            {
                if (method.getName().equals("sendString"))
                {
                    if (failSend)
                    {
                        throw new IOException("Simulated dead socket");
                    }

                    messages.add((String) arguments[0]);
                }

                return null;
            });

            session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class[]{Session.class}, (proxy, method, arguments) ->
            {
                switch (method.getName())
                {
                    case "getRemote":
                        return remote;
                    case "isOpen":
                        return closeCode == 0;
                    case "close":
                        closeCode = (Integer) arguments[0];
                        return null;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == arguments[0];
                    default:
                        return null;
                }
            });
        }

        private <T extends WsContext> T prepare(T context) throws Exception
        {
            Context upgrade = (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class[]{Context.class}, (proxy, method, arguments) ->
            {
                if (method.getName().equals("pathParam"))
                {
                    return roomId;
                }

                if (method.getName().equals("jsonMapper"))
                {
                    return JSON;
                }

                return null;
            });

            Map<String, Object> attributes = new HashMap<>();

            if (playerId != null)
            {
                attributes.put("playerID", playerId);
            }

            // Supply Javalin's upgrade/session snapshots without binding a real network socket.
            Field upgradeField = WsContext.class.getDeclaredField("upgradeCtx$delegate");
            upgradeField.setAccessible(true);
            upgradeField.set(context, kotlin.LazyKt.lazyOf(upgrade));
            Field sessionField = WsContext.class.getDeclaredField("sessionAttributes$delegate");
            sessionField.setAccessible(true);
            sessionField.set(context, kotlin.LazyKt.lazyOf(attributes));
            return context;
        }

        WsConnectContext connect() throws Exception
        {
            return prepare(new WsConnectContext("test", session));
        }

        WsMessageContext message(String json) throws Exception
        {
            return prepare(new WsMessageContext("test", session, json));
        }

        WsCloseContext closed() throws Exception
        {
            return prepare(new WsCloseContext("test", session, 1000, "reload"));
        }

        String lastMessage()
        {
            return messages.getLast();
        }
    }
}
