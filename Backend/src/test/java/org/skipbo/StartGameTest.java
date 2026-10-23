package org.skipbo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.skipbo.dto.websocket.RoomStateMessage;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.Player;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;
import org.skipbo.service.GameStateMessageFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class StartGameTest
{
    private Room readyRoom(Lobby lobby, int target, int count)
    {
        Room room = lobby.room(lobby.player("host"), target);
        for (int i = 1; i < count; i++)
        {
            Player player = lobby.player("member" + i);
            lobby.service.joinRoom(player.getId(), room.getId());
        }
        for (String id : room.getPlayerIds())
        {
            lobby.service.toggleReady(id, room.getId());
        }
        return room;
    }

    private void assertWaiting(Room room)
    {
        assertEquals(RoomState.WAITING, room.getState());
        assertNull(room.getGameState());
    }

    @Test
    void onlyHostMemberCanStartAnExistingRoom()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            expectRejected(() -> lobby.service.startRoom("member1", room.getId()));
            expectRejected(() -> lobby.service.startRoom("outsider", room.getId()));
            expectRejected(() -> lobby.service.startRoom(null, room.getId()));
            expectRejected(() -> lobby.service.startRoom("host", "missing"));
            assertWaiting(room);
        }
    }

    @Test
    void fixedRoomsDealInMembershipOrderAndStartFirstTurn()
    {
        for (int count = 2; count <= 6; count++)
        {
            try (Lobby lobby = new Lobby())
            {
                Room room = readyRoom(lobby, count, count);
                assertWaiting(room);
                List<String> orderedIds = room.getPlayerIds();
                lobby.service.startRoom("host", room.getId());
                assertEquals(RoomState.IN_GAME, room.getState());
                GameState game = room.getGameState();
                assertNotNull(game);
                assertEquals(count, game.getPlayers().size());
                int stockSize = 30;
                if (count >= 5)
                {
                    stockSize = 20;
                }
                for (int i = 0; i < count; i++)
                {
                    GamePlayerState player = game.getPlayers().get(i);
                    assertEquals(orderedIds.get(i), player.getPlayerId());
                    assertEquals(stockSize, player.getStockPile().size());
                    if (i == 0)
                    {
                        assertEquals(5, player.getHand().size());
                    }
                    else
                    {
                        assertTrue(player.getHand().isEmpty());
                    }
                }
                assertEquals(orderedIds.getFirst(), game.getCurrentPlayerId());
                assertEquals(162 - count * stockSize - 5, game.getDrawPile().size());
            }
        }
    }

    @Test
    void fixedRoomRejectsMissingPlayers()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 4, 3);
            expectRejected(() -> lobby.service.startRoom("host", room.getId()));
            assertWaiting(room);
            Player last = lobby.player("last");
            lobby.service.joinRoom(last.getId(), room.getId());
            lobby.service.toggleReady(last.getId(), room.getId());
            lobby.service.startRoom("host", room.getId());
            assertEquals(4, room.getGameState().getPlayers().size());
        }
    }

    @Test
    void anyRoomAcceptsTwoThroughSixPlayers()
    {
        for (int count = 2; count <= 6; count++)
        {
            try (Lobby lobby = new Lobby())
            {
                Room room = readyRoom(lobby, 1, count);
                lobby.service.startRoom("host", room.getId());
                assertEquals(RoomState.IN_GAME, room.getState());
                assertEquals(count, room.getGameState().getPlayers().size());
                GameStateMessage message = new GameStateMessageFactory(lobby.players).createGameStateMessage(room, "host");
                assertEquals(count, message.playerCount());
                assertEquals(1, room.getPlayerTarget());
            }
        }
    }

    @Test
    void anyRoomRejectsOnePlayerAndSeventhMember()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 1, 1);
            expectRejected(() -> lobby.service.startRoom("host", room.getId()));
            assertWaiting(room);
            for (int i = 1; i < 6; i++)
            {
                lobby.service.joinRoom(lobby.player("member" + i).getId(), room.getId());
            }
            expectRejected(() -> lobby.service.joinRoom("seventh", room.getId()));
            assertEquals(6, room.getPlayerCount());
            assertWaiting(room);
        }
    }

    @Test
    void everyMemberIncludingHostMustBeReady()
    {
        for (String unreadyId : List.of("host", "member1", "member2"))
        {
            try (Lobby lobby = new Lobby())
            {
                Room room = readyRoom(lobby, 3, 3);
                lobby.service.toggleReady(unreadyId, room.getId());
                expectRejected(() -> lobby.service.startRoom("host", room.getId()));
                assertWaiting(room);
            }
        }
    }

    @Test
    void closedRoomCannotStart()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            room.close();
            expectRejected(() -> lobby.service.startRoom("host", room.getId()));
            assertEquals(RoomState.CLOSED, room.getState());
            assertNull(room.getGameState());
        }
    }

    @Test
    void existingGameCannotBeReplacedEvenWhileWaiting()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            GameState original = new GameState(room.getPlayerIds());
            room.setGameState(original);
            expectRejected(() -> lobby.service.startRoom("host", room.getId()));
            assertSame(original, room.getGameState());
            assertEquals(RoomState.WAITING, room.getState());
        }
    }

    @Test
    void duplicateStartPreservesOriginalGameAndHand()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            lobby.service.startRoom("host", room.getId());
            GameState original = room.getGameState();
            List<?> hand = new ArrayList<>(original.getPlayers().getFirst().getHand());
            expectRejected(() -> lobby.service.startRoom("host", room.getId()));
            assertSame(original, room.getGameState());
            assertEquals(hand, original.getPlayers().getFirst().getHand());
        }
    }

    @Test
    void concurrentStartsSucceedExactlyOnce() throws Exception
    {
        try (Lobby lobby = new Lobby(); ExecutorService executor = Executors.newFixedThreadPool(2))
        {
            Room room = readyRoom(lobby, 2, 2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++)
            {
                requests.add(executor.submit(() ->
                {
                    start.await();
                    try
                    {
                        lobby.service.startRoom("host", room.getId());
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
            assertEquals(1, successes);
            assertEquals(RoomState.IN_GAME, room.getState());
            assertEquals(5, room.getGameState().getPlayers().getFirst().getHand().size());
        }
    }

    @Test
    void socketStartUsesSessionHostAndKeepsRoomStateSafe() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 1, 2);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("member1"), room);
            TestSocket observer = new TestSocket(null, "global");
            lobby.globalConnections.addConnection(observer.connect());
            lobby.service.broadcastGlobalRoomList();
            assertFalse(lobby.service.getRoomSummaries().isEmpty());
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(guest.message("{\"type\":\"START_GAME\",\"hostId\":\"host\",\"playerId\":\"host\"}"));
            assertWaiting(room);
            assertEquals(0, guest.closeCode);
            handler.onMessage(host.message("{\"type\":\"START_GAME\"}"));
            assertEquals(RoomState.IN_GAME, room.getState());
            assertNotNull(room.getGameState());
            for (TestSocket socket : List.of(host, guest))
            {
                String roomStateJson = socket.messages.get(socket.messages.size() - 2);
                RoomStateMessage message = JSON.fromJsonString(roomStateJson, RoomStateMessage.class);
                assertEquals("ROOM_STATE", message.type());
                assertEquals("IN_GAME", message.state());
                JsonNode json = JSON.getMapper().readTree(roomStateJson);
                assertEquals(8, json.size());
                for (String field : List.of("type", "roomId", "roomCode", "roomName", "playerTarget", "state", "players", "chatMessages"))
                {
                    assertTrue(json.has(field));
                }
                for (JsonNode player : json.get("players"))
                {
                    assertEquals(5, player.size());
                    for (String field : List.of("id", "username", "avatar", "ready", "host"))
                    {
                        assertTrue(player.has(field));
                    }
                }
                assertEquals(0, socket.closeCode);
            }
            assertTrue(lobby.service.getRoomSummaries().isEmpty());
            assertTrue(observer.lastMessage().contains("\"rooms\":[]"));
            GameState original = room.getGameState();
            handler.onMessage(host.message("{\"type\":\"START_GAME\"}"));
            assertSame(original, room.getGameState());
            assertEquals(0, host.closeCode);
        }
    }

    @Test
    void staleAndAnonymousSocketsCannotStart() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            TestSocket old = lobby.connect(lobby.players.findById("host"), room);
            TestSocket current = lobby.connect(lobby.players.findById("host"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(old.message("{\"type\":\"START_GAME\"}"));
            handler.onMessage(new TestSocket(null, room.getId()).message("{\"type\":\"START_GAME\"}"));
            assertWaiting(room);
            handler.onMessage(current.message("{\"type\":\"START_GAME\"}"));
            assertNotNull(room.getGameState());
        }
    }

    @Test
    void everyoneReadyStillRequiresExplicitHostStart()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = readyRoom(lobby, 2, 2);
            assertTrue(room.canStart());
            assertWaiting(room);
            expectRejected(() -> lobby.service.startRoom("member1", room.getId()));
            assertWaiting(room);
            lobby.service.startRoom("host", room.getId());
            assertEquals(RoomState.IN_GAME, room.getState());
            assertNotNull(room.getGameState());
        }
    }

}
