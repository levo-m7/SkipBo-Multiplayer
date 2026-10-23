package org.skipbo;

import org.junit.jupiter.api.Test;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.Player;
import org.skipbo.model.card.Card;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;
import org.skipbo.service.GameStateMessageFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class GameReconnectTest
{
    private Room runningRoom(Lobby lobby) throws Exception
    {
        Player host = lobby.player("host");
        Player guest = lobby.player("guest");
        Room room = lobby.room(host, 2);
        lobby.connect(host, room);
        lobby.connect(guest, room);
        lobby.service.toggleReady("host", room.getId());
        lobby.service.toggleReady("guest", room.getId());
        lobby.service.startRoom("host", room.getId());
        return room;
    }

    private GameStateMessage state(TestSocket socket)
    {
        return JSON.fromJsonString(socket.lastMessage(), GameStateMessage.class);
    }

    @Test
    void disconnectKeepsSeatGamePlayerCardsAndHostWithoutSchedulingRemoval() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState game = room.getGameState();
            GamePlayerState host = game.getPlayers().getFirst();
            List<Card> stock = new ArrayList<>(host.getStockPile());
            List<Card> hand = new ArrayList<>(host.getHand());
            TestSocket current = lobby.connect(lobby.players.findById("host"), room);
            new WebSocketHandler(lobby.service, lobby.sessions).onClose(current.closed());
            assertTrue(lobby.scheduler.getQueue().isEmpty());
            lobby.scheduler.expireAll();
            assertTrue(room.hasPlayer("host"));
            assertEquals("host", room.getHostId());
            assertSame(game, room.getGameState());
            assertSame(host, game.getPlayers().getFirst());
            assertEquals(stock, host.getStockPile());
            assertEquals(hand, host.getHand());
            assertEquals(2, room.getPlayerCount());
            assertEquals(2, game.getPlayers().size());
            assertEquals("host", game.getCurrentPlayerId());
        }
    }

    @Test
    void sameSessionReconnectRestoresCurrentViewWithoutRedealingDrawingOrRestarting() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState game = room.getGameState();
            GamePlayerState host = game.getPlayers().getFirst();
            Card moved = host.getHand().remove(0);
            host.getDiscardPiles().get(0).add(0, moved);
            List<Card> hand = new ArrayList<>(host.getHand());
            List<Card> stock = new ArrayList<>(host.getStockPile());
            List<Card> draw = new ArrayList<>(game.getDrawPile());
            List<Card> guestStock = new ArrayList<>(game.getPlayers().get(1).getStockPile());
            String expected = JSON.toJsonString(new GameStateMessageFactory(lobby.players).createGameStateMessage(room, "host"), GameStateMessage.class);
            Player profile = lobby.players.findById("host");
            TestSocket old = lobby.connect(profile, room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onClose(old.closed());
            TestSocket restored = new TestSocket("host", room.getId());
            handler.onConnect(restored.connect());
            assertSame(profile, lobby.sessions.getPlayer(restored.connect()));
            assertSame(game, room.getGameState());
            assertEquals(expected, restored.lastMessage());
            assertEquals("host", state(restored).localPlayer().playerId());
            assertEquals(1, state(restored).localPlayer().playerNumber());
            assertEquals(2, state(restored).opponents().getFirst().playerNumber());
            assertEquals(2, state(restored).playerCount());
            assertEquals(4, state(restored).localPlayer().hand().size());
            assertEquals(hand, host.getHand());
            assertEquals(stock, host.getStockPile());
            assertEquals(draw, game.getDrawPile());
            assertEquals(guestStock, game.getPlayers().get(1).getStockPile());
            assertEquals("host", game.getCurrentPlayerId());
            assertTrue(lobby.scheduler.getQueue().isEmpty());
            assertEquals(0, restored.closeCode);
        }
    }

    @Test
    void reconnectReceivesMovesMadeWhileDisconnectedAndDoesNotChangeTurn() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            handler.onClose(guest.closed());
            int discardId = room.getGameState().getPlayers().getFirst().getHand().getFirst().id();
            assertTrue(lobby.service.discardHandCard("host", room.getId(), discardId, 2));
            assertEquals("guest", room.getGameState().getCurrentPlayerId());
            List<Card> hand = new ArrayList<>(room.getGameState().getPlayers().get(1).getHand());
            int drawCount = room.getGameState().getDrawPile().size();
            TestSocket restored = new TestSocket("guest", room.getId());
            handler.onConnect(restored.connect());
            assertEquals("guest", state(restored).currentPlayerId());
            assertEquals(2, state(restored).localPlayer().playerNumber());
            assertEquals(1, state(restored).opponents().getFirst().playerNumber());
            assertEquals(5, state(restored).localPlayer().hand().size());
            assertEquals(hand, room.getGameState().getPlayers().get(1).getHand());
            assertEquals(drawCount, room.getGameState().getDrawPile().size());
            assertEquals(discardId, state(restored).opponents().getFirst().discardTopCards().get(2).id());
            assertEquals("guest", room.getGameState().getCurrentPlayerId());
        }
    }

    @Test
    void staleCloseCannotRemoveReplacementOrTriggerWaitingGrace() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket old = lobby.connect(lobby.players.findById("host"), room);
            TestSocket replacement = lobby.connect(lobby.players.findById("host"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onClose(old.closed());
            lobby.scheduler.expireAll();
            assertTrue(lobby.connections.isCurrentConnection(room.getId(), "host", replacement.connect()));
            assertTrue(room.hasPlayer("host"));
            assertTrue(lobby.scheduler.getQueue().isEmpty());
            int cardId = room.getGameState().getPlayers().getFirst().getHand().getFirst().id();
            handler.onMessage(old.message("{\"type\":\"DISCARD_HAND_CARD\",\"cardId\":" + cardId + ",\"discardPileIndex\":0}"));
            assertEquals("host", room.getGameState().getCurrentPlayerId());
            handler.onMessage(replacement.message("{\"type\":\"DISCARD_HAND_CARD\",\"cardId\":" + cardId + ",\"discardPileIndex\":0}"));
            assertEquals("guest", room.getGameState().getCurrentPlayerId());
        }
    }

    @Test
    void nonMemberCannotJoinActiveGameAndInGameLeaveRemainsRejected() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket outsider = new TestSocket(lobby.player("outsider").getId(), room.getId());
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onConnect(outsider.connect());
            assertEquals(4009, outsider.closeCode);
            assertTrue(outsider.messages.isEmpty());
            assertFalse(room.hasPlayer("outsider"));
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            handler.onMessage(host.message("{\"type\":\"LEAVE_ROOM\"}"));
            assertTrue(room.hasPlayer("host"));
            assertEquals(0, host.closeCode);
            assertEquals(RoomState.IN_GAME, room.getState());
            assertTrue(lobby.service.getRoomSummaries().isEmpty());
        }
    }

    @Test
    void finishedGameReconnectRestoresWinnerAndChatStillWorks() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            room.getGameState().setWinnerPlayerId("host");
            GameState original = room.getGameState();
            TestSocket guest = new TestSocket("guest", room.getId());
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onConnect(guest.connect());
            assertEquals("host", state(guest).winnerPlayerId());
            assertSame(original, room.getGameState());
            handler.onMessage(guest.message("{\"type\":\"CHAT_MESSAGE\",\"message\":\"Good game\"}"));
            assertEquals("Good game", room.getChatMessages().getLast().message());
            assertEquals("guest", room.getChatMessages().getLast().playerId());
            assertEquals("host", room.getGameState().getWinnerPlayerId());
        }
    }
}
