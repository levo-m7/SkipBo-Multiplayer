package org.skipbo;

import org.skipbo.dto.room.ChatMessage;
import org.skipbo.dto.room.CreateRoomRequest;
import org.skipbo.dto.room.CreateRoomResponse;
import org.skipbo.handler.RoomHandler;
import org.skipbo.handler.SessionHandler;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.Player;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.skipbo.LobbyTestSupport.*;

public class LobbyChecks
{
    public static void readyTogglesAndBroadcasts() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            TestSocket hostSocket = lobby.connect(host, room);
            TestSocket guestSocket = lobby.connect(guest, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            check(!room.isReady(host.getId()), "Initial READY must be false");
            handler.onMessage(hostSocket.message("{\"type\":\"TOGGLE_READY\"}"));
            check(room.isReady(host.getId()), "READY must become true");
            check(hostSocket.lastMessage().contains("\"ready\":true"), "Host needs authoritative state");
            check(guestSocket.lastMessage().contains("\"ready\":true"), "Guest needs authoritative state");
            handler.onMessage(hostSocket.message("{\"type\":\"TOGGLE_READY\"}"));
            check(!room.isReady(host.getId()), "READY must become false again");
            check(!guestSocket.lastMessage().contains("\"ready\":true"), "Guest received stale ready state");
            check(hostSocket.closeCode == 0 && guestSocket.closeCode == 0, "READY closed a socket");
        }
    }

    public static void profileLocksAndUnlocks() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket socket = lobby.connect(host, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            lobby.service.toggleReady(host.getId(), room.getId());
            handler.onMessage(socket.message("{\"type\":\"UPDATE_USERNAME\",\"username\":\"locked\"}"));
            handler.onMessage(socket.message("{\"type\":\"UPDATE_AVATAR\",\"avatar\":8}"));
            check(host.getUsername().equals("user") && host.getAvatar() == 1, "Ready player changed profile");
            lobby.service.toggleReady(host.getId(), room.getId());
            handler.onMessage(socket.message("{\"type\":\"UPDATE_USERNAME\",\"username\":\"open\"}"));
            handler.onMessage(socket.message("{\"type\":\"UPDATE_AVATAR\",\"avatar\":8}"));
            check(host.getUsername().equals("open") && host.getAvatar() == 8, "Unready profile remained locked");
        }
    }

    public static void membershipAndCapacity() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Player otherHost = lobby.player("otherHost");
            Room first = lobby.room(host, 2);
            Room second = lobby.room(otherHost, 2);
            lobby.service.joinRoom(guest.getId(), first.getId());
            lobby.service.joinRoom(guest.getId(), second.getId());
            check(!first.hasPlayer(guest.getId()) && second.hasPlayer(guest.getId()), "Moving rooms retained old membership");
            expectRejected(() -> lobby.service.joinRoom("extra", second.getId()));
            check(second.getPlayerCount() == 2, "Capacity changed on failed join");
            lobby.service.leaveRoom(guest.getId(), second.getId());
            check(!second.hasPlayer(guest.getId()) && !second.isReady(guest.getId()), "Leave retained player state");
            lobby.service.joinRoom(guest.getId(), first.getId());
            check(first.hasPlayer(guest.getId()), "Left player could not join another room");
        }
    }

    public static void anyRoomStopsAtSix() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = lobby.room(lobby.player("host"), 1);

            for (int index = 1; index <= 5; index++)
            {
                lobby.service.joinRoom("player" + index, room.getId());
            }

            expectRejected(() -> lobby.service.joinRoom("seventh", room.getId()));
            check(room.isFull() && room.getPlayerCount() == 6, "ANY exceeded six players");
        }
    }

    public static void concurrentJoinsRespectCapacity() throws Exception
    {
        try (Lobby lobby = new Lobby(); ExecutorService executor = Executors.newFixedThreadPool(12))
        {
            Room room = lobby.room(lobby.player("host"), 6);

            for (int index = 1; index <= 4; index++)
            {
                lobby.service.joinRoom("existing" + index, room.getId());
            }

            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> requests = new ArrayList<>();

            for (int index = 0; index < 12; index++)
            {
                String playerId = "joining" + index;
                requests.add(executor.submit(() ->
                {
                    start.await();

                    try
                    {
                        lobby.service.joinRoom(playerId, room.getId());
                        return true;
                    }
                    catch (IllegalStateException exception)
                    {
                        return false;
                    }
                }));
            }

            start.countDown();
            int successes = 0;

            for (Future<Boolean> request : requests)
            {
                if (request.get())
                {
                    successes++;
                }
            }

            check(successes == 1 && room.getPlayerCount() == 6, "Concurrent join exceeded capacity");
        }
    }

    public static void concurrentJoinsKeepOneMembership() throws Exception
    {
        try (Lobby lobby = new Lobby(); ExecutorService executor = Executors.newFixedThreadPool(2))
        {
            Room first = lobby.room(lobby.player("host1"), 2);
            Room second = lobby.room(lobby.player("host2"), 2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> requests = new ArrayList<>();

            for (Room room : List.of(first, second))
            {
                requests.add(executor.submit(() ->
                {
                    start.await();

                    try
                    {
                        lobby.service.joinRoom("samePlayer", room.getId());
                    }
                    catch (IllegalStateException exception)
                    {
                        // One request must lose the membership race.
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> request : requests)
            {
                request.get();
            }

            check(first.hasPlayer("samePlayer") != second.hasPlayer("samePlayer"), "Player joined two rooms");
        }
    }

    public static void replacementRejectsOldSocket() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket oldSocket = lobby.connect(host, room);
            TestSocket replacement = lobby.connect(host, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(oldSocket.message("{\"type\":\"TOGGLE_READY\"}"));
            handler.onMessage(oldSocket.message("{\"type\":\"LEAVE_ROOM\"}"));
            handler.onClose(oldSocket.closed());
            lobby.scheduler.expireAll();
            check(room.hasPlayer(host.getId()) && !room.isReady(host.getId()), "Stale socket changed membership/ready");
            check(lobby.connections.isCurrentConnection(room.getId(), host.getId(), replacement.connect()), "Old close removed replacement");
            handler.onMessage(replacement.message("{\"type\":\"TOGGLE_READY\"}"));
            check(room.isReady(host.getId()), "Replacement socket lost authority");
        }
    }

    public static void reconnectPreservesHostAndReady() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket socket = lobby.connect(host, room);
            lobby.service.toggleReady(host.getId(), room.getId());
            lobby.service.disconnectPlayer(host.getId(), room.getId(), socket.closed());
            check(room.hasPlayer(host.getId()), "Socket close removed membership immediately");
            check(room.getHostId().equals(host.getId()), "Socket close transferred host immediately");
            TestSocket replacement = lobby.connect(host, room);
            lobby.scheduler.expireAll();
            check(room.hasPlayer(host.getId()) && room.isReady(host.getId()), "Reconnect lost membership or READY");
            check(room.getHostId().equals(host.getId()), "Reconnect lost host");
            check(lobby.scheduler.getQueue().isEmpty(), "Reconnect retained timer in scheduler queue");
            check(replacement.lastMessage().contains("\"ready\":true"), "Reconnect missed authoritative READY");
        }
    }

    public static void timeoutTransfersHost() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            TestSocket hostSocket = lobby.connect(host, room);
            TestSocket guestSocket = lobby.connect(guest, room);
            lobby.service.toggleReady(host.getId(), room.getId());
            lobby.service.disconnectPlayer(host.getId(), room.getId(), hostSocket.closed());
            check(room.getHostId().equals(host.getId()), "Host transferred before grace expired");
            lobby.scheduler.expireAll();
            check(!room.hasPlayer(host.getId()) && !room.isReady(host.getId()), "Timeout kept player state");
            check(room.getHostId().equals(guest.getId()), "Timeout did not transfer host");
            check(guestSocket.lastMessage().contains("\"host\":true"), "New host state not broadcast");
            check(lobby.service.getRoomSummaries().size() == 1, "Timeout did not make full room available");
        }
    }

    public static void lastTimeoutDeletesRoom() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket socket = lobby.connect(host, room);
            lobby.service.disconnectPlayer(host.getId(), room.getId(), socket.closed());
            lobby.scheduler.expireAll();
            check(lobby.service.getRoom(room.getId()) == null, "Last timeout retained empty room");
            check(lobby.service.getRoomSummaries().isEmpty(), "Deleted room remained public");
            lobby.scheduler.expireAll();
        }
    }

    public static void explicitLeaveIsImmediate() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket socket = lobby.connect(host, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(socket.message("{\"type\":\"LEAVE_ROOM\"}"));
            check(lobby.service.getRoom(room.getId()) == null, "Explicit leave waited for grace");
            check(socket.lastMessage().contains("LEFT_ROOM"), "Missing LEFT_ROOM acknowledgment");
            handler.onClose(socket.closed());
            check(lobby.scheduler.getQueue().isEmpty(), "Explicit leave left a pending timer");
            lobby.scheduler.expireAll();
        }
    }

    public static void validationRejectsBadValues() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player player = lobby.player("host");

            for (String name : List.of("", " ", "x".repeat(21)))
            {
                expectRejected(() -> lobby.service.createRoom(player.getId(), new CreateRoomRequest(name, 2)));
            }

            for (Integer target : new Integer[]{null, 0, 7, -1})
            {
                expectRejected(() -> lobby.service.createRoom(player.getId(), new CreateRoomRequest("Room", target)));
            }

            expectRejected(() -> lobby.service.createRoom(player.getId(), null));
            expectRejected(() -> lobby.service.createRoom(player.getId(), new CreateRoomRequest(null, 2)));
            Room room = lobby.service.createRoom(player.getId(), new CreateRoomRequest(" Fun Room ", 2));
            check(room.getName().equals("Fun Room"), "Room name was not trimmed");
            check(java.util.UUID.fromString(room.getId()).toString().equals(room.getId()), "Room ID is not a full UUID");
            expectRejected(() -> lobby.service.joinRoom("other", room.getId().substring(0, 8)));

            for (String name : new String[]{null, "", "123456789", "has space", "bad!"})
            {
                expectRejected(() -> lobby.service.updateUsername(player, name));
            }

            for (Integer avatar : new Integer[]{null, 0, 11, -1})
            {
                expectRejected(() -> lobby.service.updateAvatar(player, avatar));
            }

            lobby.service.updateUsername(player, "A_z-09");
            lobby.service.updateAvatar(player, 10);
            check(player.getUsername().equals("A_z-09") && player.getAvatar() == 10, "Valid profile rejected");
            expectRejected(() -> lobby.service.sendChatMessage(player, room.getId(), null));
            expectRejected(() -> lobby.service.sendChatMessage(player, room.getId(), "   "));
            expectRejected(() -> lobby.service.sendChatMessage(player, room.getId(), "x".repeat(201)));
        }
    }

    public static void chatHistoryAndThrottle() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            check(lobby.service.sendChatMessage(host, room.getId(), " hello "), "First chat was throttled");
            check(room.getChatMessages().getFirst().message().equals("hello"), "Chat was not trimmed");
            check(!lobby.service.sendChatMessage(host, room.getId(), "spam"), "Immediate spam was accepted");
            String firstId = room.getChatMessages().getFirst().id();
            check(java.util.UUID.fromString(firstId) != null, "Chat ID is not a UUID");

            for (int index = 0; index < 101; index++)
            {
                room.addChatMessage(new ChatMessage("message" + index, host.getId(), "user", 1, "hello"));
            }

            check(room.getChatMessages().size() == 100, "Chat history exceeded 100");
            check(room.getChatMessages().getFirst().id().equals("message1"), "Oldest chat not removed");
            check(room.getChatMessages().getLast().id().equals("message100"), "Newest chat ID changed");
        }
    }

    public static void globalListTracksMembership() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            TestSocket observer = new TestSocket(null, "global");
            lobby.globalConnections.addConnection(observer.connect());
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 3);
            check(observer.lastMessage().contains("\"playerCount\":1"), "Creation not broadcast");
            lobby.service.joinRoom(guest.getId(), room.getId());
            check(observer.lastMessage().contains("\"playerCount\":2"), "Join count not broadcast");
            lobby.service.joinRoom("third", room.getId());
            check(observer.lastMessage().contains("\"rooms\":[]"), "Full room remained public");
            lobby.service.leaveRoom("third", room.getId());
            check(observer.lastMessage().contains("\"playerCount\":2"), "Available room not broadcast");
            lobby.service.leaveRoom(guest.getId(), room.getId());
            check(observer.lastMessage().contains("\"playerCount\":1"), "Leave count not broadcast");
            lobby.service.leaveRoom(host.getId(), room.getId());
            check(observer.lastMessage().contains("\"rooms\":[]"), "Empty room remained public");
        }
    }

    public static void lifecycleFiltersListAndPreservesInGameSeat() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            TestSocket observer = new TestSocket(null, "global");
            lobby.globalConnections.addConnection(observer.connect());
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 1);
            TestSocket hostSocket = lobby.connect(host, room);
            lobby.connect(guest, room);
            expectRejected(() -> lobby.service.startRoom(host.getId(), room.getId()));
            lobby.service.toggleReady(host.getId(), room.getId());
            lobby.service.toggleReady(guest.getId(), room.getId());
            expectRejected(() -> lobby.service.startRoom(guest.getId(), room.getId()));
            lobby.service.startRoom(host.getId(), room.getId());
            check(room.getState() == RoomState.IN_GAME, "Existing start transition failed");
            check(observer.lastMessage().contains("\"rooms\":[]"), "In-game room remained public");
            expectRejected(() -> lobby.service.toggleReady(host.getId(), room.getId()));
            expectRejected(() -> lobby.service.updateAvatar(host, 3));
            expectRejected(() -> lobby.service.updateUsername(host, "change"));
            lobby.service.disconnectPlayer(host.getId(), room.getId(), hostSocket.closed());
            lobby.scheduler.expireAll();
            check(room.hasPlayer(host.getId()), "In-game close deleted seat");
            lobby.connect(host, room);
            expectRejected(() -> lobby.service.closeRoom(guest.getId(), room.getId()));
            lobby.service.closeRoom(host.getId(), room.getId());
            check(room.getState() == RoomState.CLOSED && lobby.service.getRoom(room.getId()) == null, "Closed room was not cleaned up");
            check(lobby.connections.getConnectionCount(room.getId()) == 0, "Closed room retained sockets");
            check(observer.lastMessage().contains("\"rooms\":[]"), "Closed/deleted room remained public");
        }
    }

    public static void deadSendsDoNotStopBroadcasts() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            TestSocket dead = lobby.connect(host, room);
            TestSocket good = lobby.connect(guest, room);
            dead.failSend = true;
            lobby.service.toggleReady(guest.getId(), room.getId());
            check(good.lastMessage().contains("\"ready\":true"), "Dead socket interrupted room broadcast");
            check(lobby.connections.getConnectionCount(room.getId()) == 1, "Failed socket retained");
            lobby.scheduler.expireAll();
            check(!room.hasPlayer(host.getId()), "Failed send never expired membership");
            TestSocket deadObserver = new TestSocket(null, "global");
            TestSocket goodObserver = new TestSocket(null, "global");
            deadObserver.failSend = true;
            lobby.globalConnections.addConnection(deadObserver.connect());
            lobby.globalConnections.addConnection(goodObserver.connect());
            lobby.service.broadcastGlobalRoomList();
            check(goodObserver.lastMessage().contains("ROOM_LIST"), "Dead socket interrupted global broadcast");
            check(lobby.globalConnections.getConnectionCount() == 1, "Dead global socket retained");
        }
    }

    public static void malformedWebSocketMessagesAreSafe() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            TestSocket socket = lobby.connect(host, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);

            for (String message : List.of("not json", "null", "{}", "[]", "{\"type\":null}", "{\"type\":7}",
                    "{\"type\":\"UNKNOWN\"}", "{\"type\":\"CHAT_MESSAGE\"}", "{\"type\":\"UPDATE_AVATAR\",\"avatar\":1.5}",
                    "{\"type\":\"UPDATE_AVATAR\",\"avatar\":\"2\"}", "{\"type\":\"UPDATE_USERNAME\",\"username\":123}"))
            {
                handler.onMessage(socket.message(message));
            }

            check(socket.closeCode == 0 && host.getAvatar() == 1 && host.getUsername().equals("user"), "Malformed input changed player/closed socket");
            handler.onMessage(socket.message("{\"type\":\"TOGGLE_READY\"}"));
            check(room.isReady(host.getId()), "Valid action failed after malformed input");
        }
    }

    public static void sessionIdentityCannotBeForged() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            lobby.connect(host, room);
            TestSocket guestSocket = lobby.connect(guest, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(guestSocket.message("{\"type\":\"TOGGLE_READY\",\"playerId\":\"host\",\"ready\":false,\"host\":true}"));
            check(!room.isReady(host.getId()) && room.getHostId().equals(host.getId()), "Forged identity changed host state");
            handler.onMessage(guestSocket.message("{\"type\":\"CHAT_MESSAGE\",\"playerId\":\"host\",\"username\":\"host\",\"message\":\"hello\"}"));

            handler.onMessage(guestSocket.message("{\"type\":\"CHAT_MESSAGE\",\"username\":\"host\",\"avatar\":10,\"message\":\"hello\"}"));
            check(room.getChatMessages().size() == 1, "Valid chat was not processed");

            for (ChatMessage chat : room.getChatMessages())
            {
                check(chat.playerId().equals(guest.getId()), "Forged identity changed chat author");
                check(chat.username().equals(guest.getUsername()) && chat.avatar() == guest.getAvatar(), "Client supplied chat profile was trusted");
            }

            TestSocket noSession = new TestSocket(null, room.getId());
            handler.onMessage(noSession.message("{\"type\":\"TOGGLE_READY\"}"));
            handler.onConnect(noSession.connect());
            check(noSession.closeCode == 4001, "Anonymous socket was admitted");
            TestSocket outsider = new TestSocket(lobby.player("outsider").getId(), room.getId());
            handler.onMessage(outsider.message("{\"type\":\"LEAVE_ROOM\"}"));
            check(room.getPlayerCount() == 2, "Nonmember mutated room");
        }
    }

    public static void httpValidationAndResponse() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            RoomHandler handler = new RoomHandler(lobby.service, lobby.sessions);

            for (String body : List.of("bad json", "null", "{}", "[]", "{\"roomName\":\"Room\"}",
                    "{\"roomName\":\"Room\",\"playerTarget\":\"2\"}", "{\"roomName\":\"Room\",\"playerTarget\":2.5}"))
            {
                TestHttp request = new TestHttp(host.getId(), body);
                handler.createRoom(request.context);
                check(request.status == 400, "Malformed create did not return 400: " + body);
            }

            TestHttp request = new TestHttp(host.getId(), "{\"roomName\":\" Fun Room \",\"playerTarget\":2}");
            handler.createRoom(request.context);
            check(request.status == 201 && request.response instanceof CreateRoomResponse, "Valid create failed");
            CreateRoomResponse response = (CreateRoomResponse) request.response;
            check(response.id().equals(response.roomId()), "Frontend-compatible id changed");
            check(response.roomCode().equals(response.id().substring(0, 8)), "Display code mismatch");
            String json = JSON.toJsonString(response, CreateRoomResponse.class);
            check(!json.contains("playerIds") && !json.contains("readyStates") && !json.contains("hostId"), "Create exposed model internals");
            TestHttp duplicate = new TestHttp(host.getId(), "{\"roomName\":\"Second\",\"playerTarget\":2}");
            handler.createRoom(duplicate.context);
            check(duplicate.status == 201, "Creating another room did not clean up old membership");
            CreateRoomResponse replacement = (CreateRoomResponse) duplicate.response;
            check(lobby.service.getRoom(response.roomId()) == null, "Empty old room was not deleted");
            check(lobby.service.getRoom(replacement.roomId()).hasPlayer(host.getId()), "New room lost session player");
        }
    }

    public static void httpProfileLockAndMalformedInput() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.room(host, 2);
            SessionHandler handler = new SessionHandler(lobby.sessions, lobby.service);

            for (String body : List.of("invalid", "null", "{}", "{\"username\":123}", "{\"username\":\"bad name\"}"))
            {
                TestHttp request = new TestHttp(host.getId(), body);
                handler.updateUsername(request.context);
                check(request.status == 400, "Invalid username did not return 400");
            }

            for (String body : List.of("invalid", "null", "{}", "{\"avatar\":11}", "{\"avatar\":1.5}", "{\"avatar\":\"2\"}"))
            {
                TestHttp request = new TestHttp(host.getId(), body);
                handler.updateAvatar(request.context);
                check(request.status == 400, "Invalid avatar did not return 400");
            }

            lobby.service.toggleReady(host.getId(), room.getId());
            TestHttp username = new TestHttp(host.getId(), "{\"username\":\"newname\"}");
            TestHttp avatar = new TestHttp(host.getId(), "{\"avatar\":7}");
            handler.updateUsername(username.context);
            handler.updateAvatar(avatar.context);
            check(username.status == 409 && avatar.status == 409, "HTTP bypassed ready profile lock");
            check(host.getUsername().equals("user") && host.getAvatar() == 1, "Locked profile was changed");
            lobby.service.toggleReady(host.getId(), room.getId());
            handler.updateUsername(username.context);
            handler.updateAvatar(avatar.context);
            check(host.getUsername().equals("newname") && host.getAvatar() == 7, "HTTP profile did not unlock");
        }
    }

    public static void abandonedCreationExpires() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = lobby.room(lobby.player("host"), 2);
            lobby.scheduler.expireAll();
            check(lobby.service.getRoom(room.getId()) == null, "Never-connected creator leaked a room");
        }
    }

    public static void explicitHostLeaveTransfersHost() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            TestSocket hostSocket = lobby.connect(host, room);
            TestSocket guestSocket = lobby.connect(guest, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(hostSocket.message("{\"type\":\"LEAVE_ROOM\"}"));
            check(room.getHostId().equals(guest.getId()), "Explicit leave did not transfer host");
            check(guestSocket.lastMessage().contains("\"host\":true"), "New host not broadcast");
            check(lobby.connections.getConnectionCount(room.getId()) == 1, "Leaving host kept authority");
        }
    }

    public static void rejectedSocketJoinsAreGraceful() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = lobby.room(lobby.player("host"), 2);
            lobby.service.joinRoom(lobby.player("guest").getId(), room.getId());
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            TestSocket full = new TestSocket(lobby.player("extra").getId(), room.getId());
            handler.onConnect(full.connect());
            check(full.closeCode == 4009 && room.getPlayerCount() == 2, "Full room socket was admitted");
            TestSocket shortCode = new TestSocket("extra", room.getId().substring(0, 8));
            handler.onConnect(shortCode.connect());
            check(shortCode.closeCode == 4004, "Display code was accepted as room ID");
        }
    }

    public static void oldTimeoutCannotRemoveNewMembership() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room first = lobby.room(host, 2);
            TestSocket firstSocket = lobby.connect(host, first);
            lobby.service.disconnectPlayer(host.getId(), first.getId(), firstSocket.closed());
            lobby.service.leaveRoom(host.getId(), first.getId());
            Room second = lobby.room(host, 2);
            lobby.connect(host, second);
            lobby.scheduler.expireAll();
            check(second.hasPlayer(host.getId()) && lobby.service.getRoom(second.getId()) == second, "Old timer removed new membership");
        }
    }

    public static void concurrentSessionInitializationKeepsIdentity() throws Exception
    {
        try (Lobby lobby = new Lobby(); ExecutorService executor = Executors.newFixedThreadPool(2))
        {
            java.util.Map<String, Object> attributes = new java.util.concurrent.ConcurrentHashMap<>();
            jakarta.servlet.http.HttpSession session = (jakarta.servlet.http.HttpSession) java.lang.reflect.Proxy.newProxyInstance(
                    jakarta.servlet.http.HttpSession.class.getClassLoader(), new Class[]{jakarta.servlet.http.HttpSession.class},
                    (proxy, method, arguments) -> null);
            jakarta.servlet.http.HttpServletRequest request = (jakarta.servlet.http.HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                    jakarta.servlet.http.HttpServletRequest.class.getClassLoader(), new Class[]{jakarta.servlet.http.HttpServletRequest.class},
                    (proxy, method, arguments) -> session);
            io.javalin.http.Context context = (io.javalin.http.Context) java.lang.reflect.Proxy.newProxyInstance(
                    io.javalin.http.Context.class.getClassLoader(), new Class[]{io.javalin.http.Context.class}, (proxy, method, arguments) ->
            {
                if (method.getName().equals("req"))
                {
                    return request;
                }

                if (method.getName().equals("sessionAttribute"))
                {
                    if (arguments.length == 2)
                    {
                        attributes.put((String) arguments[0], arguments[1]);
                        return proxy;
                    }

                    return attributes.get(arguments[0]);
                }

                return null;
            });
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Player>> requests = new ArrayList<>();

            for (int index = 0; index < 2; index++)
            {
                requests.add(executor.submit(() ->
                {
                    start.await();
                    return lobby.sessions.createPlayer(context);
                }));
            }

            start.countDown();
            Player first = requests.getFirst().get();
            Player second = requests.getLast().get();
            check(first == second && first == lobby.sessions.getPlayer(context), "Concurrent session initialization replaced identity");
        }
    }

    public static void main(String[] arguments) throws Exception
    {
        readyTogglesAndBroadcasts();
        profileLocksAndUnlocks();
        membershipAndCapacity();
        anyRoomStopsAtSix();
        concurrentJoinsRespectCapacity();
        concurrentJoinsKeepOneMembership();
        replacementRejectsOldSocket();
        reconnectPreservesHostAndReady();
        timeoutTransfersHost();
        lastTimeoutDeletesRoom();
        explicitLeaveIsImmediate();
        validationRejectsBadValues();
        chatHistoryAndThrottle();
        globalListTracksMembership();
        lifecycleFiltersListAndPreservesInGameSeat();
        deadSendsDoNotStopBroadcasts();
        malformedWebSocketMessagesAreSafe();
        sessionIdentityCannotBeForged();
        httpValidationAndResponse();
        httpProfileLockAndMalformedInput();
        abandonedCreationExpires();
        explicitHostLeaveTransfersHost();
        rejectedSocketJoinsAreGraceful();
        oldTimeoutCannotRemoveNewMembership();
        concurrentSessionInitializationKeepsIdentity();
        System.out.println("PASS: all 25 lobby foundation checks");
    }
}
