package org.skipbo;

import org.junit.jupiter.api.Test;
import org.skipbo.dto.room.CreateRoomRequest;
import org.skipbo.dto.websocket.RoomStateMessage;
import org.skipbo.model.Player;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class RoomMembershipTest
{
    @Test
    void twentyCharacterRoomNameIsAcceptedAfterTrim()
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Room room = lobby.service.createRoom(host.getId(), new CreateRoomRequest("  12345678901234567890  ", 2));
            assertEquals("12345678901234567890", room.getName());
            assertEquals(20, room.getName().length());
        }
    }

    @Test
    void twentyOneCharacterRoomNameIsRejected()
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            expectRejected(() -> lobby.service.createRoom(host.getId(), new CreateRoomRequest("123456789012345678901", 2)));
            assertTrue(lobby.rooms.getAllRooms().isEmpty());
        }
    }

    @Test
    void blankRoomNamesAreRejected()
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            for (String name : List.of("", " ", "   ", "\t\n"))
            {
                expectRejected(() -> lobby.service.createRoom(host.getId(), new CreateRoomRequest(name, 2)));
            }
            assertTrue(lobby.rooms.getAllRooms().isEmpty());
        }
    }

    @Test
    void joiningAnotherRoomUsesNormalRemovalAndTransfersOldHost() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player moving = lobby.player("moving");
            moving.setUsername("Alex");
            moving.setAvatar(7);
            Player remaining = lobby.player("remaining");
            Room old = lobby.room(moving, 1);
            TestSocket oldSocket = lobby.connect(moving, old);
            TestSocket remainingSocket = lobby.connect(remaining, old);
            lobby.service.toggleReady(moving.getId(), old.getId());
            lobby.service.sendChatMessage(remaining, old.getId(), "stay here");
            List<?> chat = old.getChatMessages();
            Room target = lobby.room(lobby.player("targetHost"), 1);
            TestSocket observer = new TestSocket(null, "global");
            lobby.globalConnections.addConnection(observer.connect());
            assertSame(target, lobby.service.joinRoom(moving.getId(), target.getId()));
            assertFalse(old.hasPlayer(moving.getId()));
            assertEquals(List.of(remaining.getId()), old.getPlayerIds());
            assertEquals(remaining.getId(), old.getHostId());
            assertSame(old, lobby.service.getRoom(old.getId()));
            assertEquals(chat, old.getChatMessages());
            assertFalse(lobby.connections.hasConnection(old.getId(), moving.getId()));
            assertTrue(target.hasPlayer(moving.getId()));
            assertFalse(target.isReady(moving.getId()));
            RoomStateMessage message = JSON.fromJsonString(remainingSocket.lastMessage(), RoomStateMessage.class);
            assertEquals(1, message.players().size());
            assertEquals(remaining.getId(), message.players().getFirst().id());
            assertTrue(message.players().getFirst().host());
            assertTrue(observer.lastMessage().contains(old.getId()));
            assertTrue(observer.lastMessage().contains(target.getId()));
            assertSame(moving, lobby.players.findById(moving.getId()));
            assertEquals("Alex", moving.getUsername());
            assertEquals(7, moving.getAvatar());
            lobby.service.disconnectPlayer(moving.getId(), old.getId(), oldSocket.closed());
            lobby.connect(moving, target);
            lobby.scheduler.expireAll();
            assertTrue(target.hasPlayer(moving.getId()));
        }
    }

    @Test
    void creatingAnotherRoomDeletesEmptyOldRoomAndKeepsIdentity() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player moving = lobby.player("moving");
            moving.setUsername("Alex");
            moving.setAvatar(4);
            Room old = lobby.room(moving, 2);
            TestSocket oldSocket = lobby.connect(moving, old);
            Room created = lobby.room(moving, 3);
            assertNull(lobby.service.getRoom(old.getId()));
            assertEquals(0, old.getPlayerCount());
            assertEquals(0, lobby.connections.getConnectionCount(old.getId()));
            assertEquals(List.of(moving.getId()), created.getPlayerIds());
            assertEquals(moving.getId(), created.getHostId());
            assertSame(moving, lobby.players.findById(moving.getId()));
            assertEquals("Alex", moving.getUsername());
            assertEquals(4, moving.getAvatar());
            assertSame(moving, lobby.sessions.getPlayer(new TestHttp(moving.getId(), null).context));
            lobby.service.disconnectPlayer(moving.getId(), old.getId(), oldSocket.closed());
            lobby.connect(moving, created);
            lobby.scheduler.expireAll();
            assertTrue(created.hasPlayer(moving.getId()));
        }
    }

    @Test
    void joiningAnotherRoomDeletesEmptyOldRoom()
    {
        try (Lobby lobby = new Lobby())
        {
            Player moving = lobby.player("moving");
            Room old = lobby.room(moving, 2);
            Room target = lobby.room(lobby.player("targetHost"), 2);
            lobby.service.joinRoom(moving.getId(), target.getId());
            assertNull(lobby.service.getRoom(old.getId()));
            assertEquals(0, old.getPlayerCount());
            assertTrue(target.hasPlayer(moving.getId()));
        }
    }

    @Test
    void leavingOldActiveGameCancelsItBeforeRemovalForJoinAndCreate() throws Exception
    {
        for (boolean create : List.of(false, true))
        {
            try (Lobby lobby = new Lobby())
            {
                Player moving = lobby.player("moving");
                Player remaining = lobby.player("remaining");
                Room old = lobby.room(moving, 1);
                lobby.connect(moving, old);
                TestSocket remainingSocket = lobby.connect(remaining, old);
                lobby.service.sendChatMessage(remaining, old.getId(), "saved chat");
                lobby.service.toggleReady(moving.getId(), old.getId());
                lobby.service.toggleReady(remaining.getId(), old.getId());
                lobby.service.startRoom(moving.getId(), old.getId());
                GameState original = old.getGameState();
                Room target;
                if (create)
                {
                    target = lobby.room(moving, 2);
                }
                else
                {
                    target = lobby.room(lobby.player("targetHost"), 2);
                    lobby.service.joinRoom(moving.getId(), target.getId());
                }
                assertEquals(RoomState.WAITING, old.getState());
                assertNull(old.getGameState());
                assertNull(original.getWinnerPlayerId());
                assertEquals(List.of(remaining.getId()), old.getPlayerIds());
                assertEquals(remaining.getId(), old.getHostId());
                assertFalse(old.isReady(remaining.getId()));
                assertFalse(lobby.connections.hasConnection(old.getId(), moving.getId()));
                assertTrue(target.hasPlayer(moving.getId()));
                assertEquals("saved chat", old.getChatMessages().getFirst().message());
                RoomStateMessage message = JSON.fromJsonString(remainingSocket.lastMessage(), RoomStateMessage.class);
                assertEquals("WAITING", message.state());
                assertEquals(1, message.players().size());
                assertFalse(message.players().getFirst().ready());
            }
        }
    }

    @Test
    void reconnectToSameActiveRoomPreservesGameCardsOrderAndReadyState() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player host = lobby.player("host");
            Player guest = lobby.player("guest");
            Room room = lobby.room(host, 2);
            TestSocket old = lobby.connect(host, room);
            lobby.connect(guest, room);
            lobby.service.toggleReady(host.getId(), room.getId());
            lobby.service.toggleReady(guest.getId(), room.getId());
            lobby.service.startRoom(host.getId(), room.getId());
            GameState game = room.getGameState();
            List<?> hand = new ArrayList<>(game.getPlayers().getFirst().getHand());
            assertSame(room, lobby.service.joinRoom(host.getId(), room.getId()));
            TestSocket replacement = lobby.connect(host, room);
            lobby.service.disconnectPlayer(host.getId(), room.getId(), old.closed());
            assertSame(game, room.getGameState());
            assertEquals(hand, game.getPlayers().getFirst().getHand());
            assertEquals(List.of(host.getId(), guest.getId()), room.getPlayerIds());
            assertEquals("host", game.getCurrentPlayerId());
            assertTrue(room.isReady(host.getId()));
            assertTrue(lobby.connections.isCurrentConnection(room.getId(), host.getId(), replacement.connect()));
        }
    }

    @Test
    void allStaleRoomsAreCleanedAndExistingTargetMembershipIsPreserved() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player moving = lobby.player("moving");
            Room target = lobby.room(lobby.player("targetHost"), 1);
            lobby.connect(moving, target);
            target.setReady(moving.getId(), true);
            List<String> targetOrder = target.getPlayerIds();
            Room first = lobby.room(lobby.player("firstHost"), 1);
            Room second = lobby.room(lobby.player("secondHost"), 1);
            assertTrue(first.addPlayer(moving.getId()));
            assertTrue(second.addPlayer(moving.getId()));
            for (String id : second.getPlayerIds())
            {
                second.setReady(id, true);
            }
            lobby.service.startRoom(second.getHostId(), second.getId());
            TestSocket replacement = lobby.connect(moving, target);
            assertEquals(targetOrder, target.getPlayerIds());
            assertTrue(target.isReady(moving.getId()));
            assertFalse(first.hasPlayer(moving.getId()));
            assertFalse(second.hasPlayer(moving.getId()));
            assertEquals(RoomState.WAITING, second.getState());
            assertNull(second.getGameState());
            assertTrue(lobby.connections.isCurrentConnection(target.getId(), moving.getId(), replacement.connect()));
            int memberships = 0;
            for (Room room : lobby.rooms.getAllRooms())
            {
                if (room.hasPlayer(moving.getId()))
                {
                    memberships++;
                }
            }
            assertEquals(1, memberships);
        }
    }

    @Test
    void invalidDestinationOrCreateRequestDoesNotRemoveCurrentMembership()
    {
        try (Lobby lobby = new Lobby())
        {
            Player moving = lobby.player("moving");
            Room old = lobby.room(moving, 2);
            Room full = lobby.room(lobby.player("targetHost"), 2);
            lobby.service.joinRoom(lobby.player("guest").getId(), full.getId());
            expectRejected(() -> lobby.service.joinRoom(moving.getId(), full.getId()));
            expectRejected(() -> lobby.service.joinRoom(moving.getId(), "missing-room"));
            expectRejected(() -> lobby.service.createRoom(moving.getId(), new CreateRoomRequest("x".repeat(21), 2)));
            expectRejected(() -> lobby.service.createRoom(moving.getId(), new CreateRoomRequest("Valid", 7)));
            assertSame(old, lobby.service.getRoom(old.getId()));
            assertTrue(old.hasPlayer(moving.getId()));
            assertFalse(full.hasPlayer(moving.getId()));
        }
    }
}
