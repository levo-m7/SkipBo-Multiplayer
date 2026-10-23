package org.skipbo;

import org.junit.jupiter.api.Test;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.card.Card;
import org.skipbo.model.card.CardType;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class GamePlayTest
{
    private Card card(int id, int value)
    {
        return new Card(id, CardType.NUMBER, value);
    }

    private Room runningRoom(Lobby lobby)
    {
        Room room = lobby.room(lobby.player("host"), 2);
        lobby.service.joinRoom(lobby.player("guest").getId(), room.getId());
        room.setReady("host", true);
        room.setReady("guest", true);
        lobby.service.startRoom("host", room.getId());
        GameState game = new GameState(room.getPlayerIds());
        game.setCurrentPlayerId("host");
        GamePlayerState host = game.getPlayers().get(0);
        host.getHand().addAll(List.of(card(101, 1), card(109, 9)));
        host.getStockPile().addAll(List.of(card(102, 2), card(108, 8)));
        host.getDiscardPiles().get(0).addAll(List.of(card(103, 3), card(107, 7)));
        game.getPlayers().get(1).getHand().add(card(206, 6));
        for (int value = 4; value <= 8; value++)
        {
            game.getDrawPile().add(card(300 + value, value));
        }
        room.setGameState(game);
        return room;
    }

    private GameStateMessage lastState(TestSocket socket)
    {
        return JSON.fromJsonString(socket.lastMessage(), GameStateMessage.class);
    }

    private List<List<Card>> cardSnapshot(GameState game)
    {
        List<List<Card>> piles = new ArrayList<>();
        piles.add(List.copyOf(game.getDrawPile()));
        piles.add(List.copyOf(game.getCompletedCards()));
        for (List<Card> pile : game.getBuildingPiles())
        {
            piles.add(List.copyOf(pile));
        }
        for (GamePlayerState player : game.getPlayers())
        {
            piles.add(List.copyOf(player.getHand()));
            piles.add(List.copyOf(player.getStockPile()));
            for (List<Card> pile : player.getDiscardPiles())
            {
                piles.add(List.copyOf(pile));
            }
        }
        return piles;
    }

    @Test
    void handStockAndDiscardBuildingActionsBroadcastPersonalizedState() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            int hostMessages = host.messages.size();
            int guestMessages = guest.messages.size();
            handler.onMessage(host.message("{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0}"));
            handler.onMessage(host.message("{\"type\":\"PLAY_STOCK_CARD\",\"buildingPileIndex\":0}"));
            handler.onMessage(host.message("{\"type\":\"PLAY_DISCARD_CARD\",\"discardPileIndex\":0,\"buildingPileIndex\":0}"));
            assertEquals(List.of(card(103, 3), card(102, 2), card(101, 1)), room.getGameState().getBuildingPiles().get(0));
            assertEquals(List.of(card(109, 9)), room.getGameState().getPlayers().getFirst().getHand());
            assertEquals(List.of(card(108, 8)), room.getGameState().getPlayers().getFirst().getStockPile());
            assertEquals(List.of(card(107, 7)), room.getGameState().getPlayers().getFirst().getDiscardPiles().get(0));
            assertEquals("host", room.getGameState().getCurrentPlayerId());
            assertEquals(hostMessages + 3, host.messages.size());
            assertEquals(guestMessages + 3, guest.messages.size());
            assertEquals("host", lastState(host).localPlayer().playerId());
            assertEquals("guest", lastState(guest).localPlayer().playerId());
            assertEquals(1, lastState(guest).opponents().getFirst().handCount());
            assertNotEquals(host.lastMessage(), guest.lastMessage());
        }
    }

    @Test
    void endingDiscardDrawsForNextPlayerAndBroadcastsNewTurn() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(host.message("{\"type\":\"DISCARD_HAND_CARD\",\"cardId\":101,\"discardPileIndex\":0}"));
            assertEquals(List.of(card(101, 1), card(103, 3), card(107, 7)), room.getGameState().getPlayers().getFirst().getDiscardPiles().get(0));
            assertEquals(List.of(card(109, 9)), room.getGameState().getPlayers().getFirst().getHand());
            assertEquals("guest", lastState(host).currentPlayerId());
            assertEquals("guest", lastState(guest).currentPlayerId());
            assertEquals(5, lastState(guest).localPlayer().hand().size());
            handler.onMessage(guest.message("{\"type\":\"DISCARD_HAND_CARD\",\"cardId\":206,\"discardPileIndex\":3}"));
            assertEquals("host", room.getGameState().getCurrentPlayerId());
            assertEquals(2, lastState(host).localPlayer().hand().size());
        }
    }

    @Test
    void finalHandCardIsRefilledBeforeBroadcastWithoutChangingTurn() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            room.getGameState().getPlayers().getFirst().getHand().remove(1);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            new WebSocketHandler(lobby.service, lobby.sessions).onMessage(host.message("{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0}"));
            assertEquals(5, lastState(host).localPlayer().hand().size());
            assertEquals(5, lastState(guest).opponents().getFirst().handCount());
            assertEquals("host", lastState(host).currentPlayerId());
            assertEquals(0, lastState(host).drawPileCount());
        }
    }

    @Test
    void finalStockMoveBroadcastsWinnerAndBlocksAllLaterMoves() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState game = room.getGameState();
            game.getBuildingPiles().get(0).add(card(401, 1));
            game.getPlayers().getFirst().getStockPile().remove(1);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(host.message("{\"type\":\"PLAY_STOCK_CARD\",\"buildingPileIndex\":0}"));
            assertEquals("host", lastState(host).winnerPlayerId());
            assertEquals("host", lastState(guest).winnerPlayerId());
            assertEquals("host", game.getCurrentPlayerId());
            assertTrue(game.getPlayers().getFirst().getStockPile().isEmpty());
            assertEquals(RoomState.IN_GAME, room.getState());
            List<List<Card>> before = cardSnapshot(game);
            int messages = host.messages.size();
            assertFalse(lobby.service.playHandCard("host", room.getId(), 101, 1));
            assertFalse(lobby.service.playStockCard("host", room.getId(), 1));
            assertFalse(lobby.service.playDiscardCard("host", room.getId(), 0, 0));
            assertFalse(lobby.service.discardHandCard("host", room.getId(), 109, 0));
            assertEquals(before, cardSnapshot(game));
            assertEquals(messages, host.messages.size());
            assertEquals(0, host.closeCode);
            assertEquals(0, guest.closeCode);
        }
    }

    @Test
    void cheatingAndIllegalMovesLeaveAllPilesTurnAndSocketsUnchanged() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState game = room.getGameState();
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            List<List<Card>> before = cardSnapshot(game);
            int messages = host.messages.size();
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            for (String message : List.of(
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":9999,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":206,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":109,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_STOCK_CARD\",\"cardId\":108,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_DISCARD_CARD\",\"cardId\":107,\"discardPileIndex\":0,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_DISCARD_CARD\",\"discardPileIndex\":3,\"buildingPileIndex\":0}"))
            {
                handler.onMessage(host.message(message));
            }
            for (String type : List.of("PLAY_HAND_CARD", "PLAY_STOCK_CARD", "PLAY_DISCARD_CARD", "DISCARD_HAND_CARD"))
            {
                handler.onMessage(guest.message("{\"type\":\"" + type + "\",\"cardId\":206,\"discardPileIndex\":0,\"buildingPileIndex\":0}"));
            }
            for (int index : List.of(-1, 4, 999))
            {
                assertFalse(lobby.service.playHandCard("host", room.getId(), 101, index));
                assertFalse(lobby.service.playStockCard("host", room.getId(), index));
                assertFalse(lobby.service.playDiscardCard("host", room.getId(), 0, index));
                assertFalse(lobby.service.playDiscardCard("host", room.getId(), index, 0));
                assertFalse(lobby.service.discardHandCard("host", room.getId(), 101, index));
            }
            assertEquals(before, cardSnapshot(game));
            assertEquals("host", game.getCurrentPlayerId());
            assertNull(game.getWinnerPlayerId());
            assertEquals(messages + 6, host.messages.size());
            assertEquals(0, host.closeCode);
            assertEquals(0, guest.closeCode);
        }
    }

    @Test
    void replayingAnAlreadyPlayedCardFailsWithoutAnotherBroadcast() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            String message = "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0}";
            handler.onMessage(host.message(message));
            int count = host.messages.size();
            List<List<Card>> before = cardSnapshot(room.getGameState());
            handler.onMessage(host.message(message));
            assertEquals(count + 1, host.messages.size());
            assertTrue(host.lastMessage().contains("GAME_ACTION_REJECTED"));
            assertEquals(before, cardSnapshot(room.getGameState()));
        }
    }

    @Test
    void gameplayRequiresRoomMembershipInGameStateAndAnAttachedGame() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            expectRejected(() -> lobby.service.playHandCard("outsider", room.getId(), 101, 0));
            expectRejected(() -> lobby.service.playStockCard("outsider", room.getId(), 0));
            expectRejected(() -> lobby.service.playDiscardCard("outsider", room.getId(), 0, 0));
            expectRejected(() -> lobby.service.discardHandCard("outsider", room.getId(), 101, 0));
            expectRejected(() -> lobby.service.playStockCard("host", "missing", 0));
            room.setGameState(null);
            expectRejected(() -> lobby.service.playHandCard("host", room.getId(), 101, 0));
            expectRejected(() -> lobby.service.playStockCard("host", room.getId(), 0));
            expectRejected(() -> lobby.service.playDiscardCard("host", room.getId(), 0, 0));
            expectRejected(() -> lobby.service.discardHandCard("host", room.getId(), 101, 0));
            Room waiting = lobby.room(lobby.player("waitingHost"), 2);
            expectRejected(() -> lobby.service.playHandCard("waitingHost", waiting.getId(), 101, 0));
            expectRejected(() -> lobby.service.playStockCard("waitingHost", waiting.getId(), 0));
            expectRejected(() -> lobby.service.playDiscardCard("waitingHost", waiting.getId(), 0, 0));
            expectRejected(() -> lobby.service.discardHandCard("waitingHost", waiting.getId(), 101, 0));
            assertEquals(RoomState.WAITING, waiting.getState());
            assertNull(waiting.getGameState());
        }
    }

    @Test
    void malformedMissingNullAndUnknownMessagesDoNotChangeGame() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            List<List<Card>> before = cardSnapshot(room.getGameState());
            int count = host.messages.size();
            for (String message : List.of("bad json", "null", "{}", "[]", "{\"type\":null}",
                    "{\"type\":\"DRAW_CARD\"}", "{\"type\":\"END_TURN\"}", "{\"type\":\"UNKNOWN_GAME\"}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101.5,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":\"101\",\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":true,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":{},\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":2147483648,\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101}",
                    "{\"type\":\"PLAY_STOCK_CARD\",\"buildingPileIndex\":null}",
                    "{\"type\":\"PLAY_DISCARD_CARD\",\"buildingPileIndex\":0}",
                    "{\"type\":\"PLAY_DISCARD_CARD\",\"discardPileIndex\":0}",
                    "{\"type\":\"DISCARD_HAND_CARD\",\"cardId\":101}",
                    "{\"type\":\"DISCARD_HAND_CARD\",\"discardPileIndex\":0}"))
            {
                handler.onMessage(host.message(message));
            }
            assertEquals(before, cardSnapshot(room.getGameState()));
            assertEquals("host", room.getGameState().getCurrentPlayerId());
            assertEquals(count + 12, host.messages.size());
            for (int i = count; i < host.messages.size(); i++)
            {
                assertTrue(host.messages.get(i).contains("GAME_ACTION_REJECTED"));
            }
            assertEquals(0, host.closeCode);
            handler.onMessage(host.message("{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0}"));
            assertEquals(1, room.getGameState().getBuildingPiles().get(0).size());
        }
    }

    @Test
    void forgedAnonymousNonMemberAndStaleSocketsCannotAct() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket old = lobby.connect(lobby.players.findById("host"), room);
            TestSocket current = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            List<List<Card>> before = cardSnapshot(room.getGameState());
            String play = "{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0}";
            handler.onMessage(old.message(play));
            handler.onMessage(new TestSocket(null, room.getId()).message(play));
            handler.onMessage(new TestSocket(lobby.player("outsider").getId(), room.getId()).message(play));
            handler.onMessage(guest.message("{\"type\":\"PLAY_HAND_CARD\",\"cardId\":101,\"buildingPileIndex\":0,\"playerId\":\"host\"}"));
            handler.onMessage(guest.message(play));
            assertEquals(before, cardSnapshot(room.getGameState()));
            handler.onClose(old.closed());
            assertTrue(lobby.connections.isCurrentConnection(room.getId(), "host", current.connect()));
            handler.onMessage(current.message(play));
            assertEquals(1, room.getGameState().getBuildingPiles().get(0).size());
        }
    }

    @Test
    void validMoveSurvivesOneBrokenSocketAndOtherPlayerReceivesUpdate() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket host = lobby.connect(lobby.players.findById("host"), room);
            TestSocket guest = lobby.connect(lobby.players.findById("guest"), room);
            guest.failSend = true;
            assertTrue(lobby.service.playHandCard("host", room.getId(), 101, 0));
            assertEquals(1, lastState(host).buildingPiles().get(0).size());
            assertTrue(room.hasPlayer("guest"));
            assertEquals(1, lobby.connections.getConnectionCount(room.getId()));
            TestSocket restored = lobby.connect(lobby.players.findById("guest"), room);
            assertEquals(1, lastState(restored).buildingPiles().get(0).size());
        }
    }

    @Test
    void concurrentActionsCannotPlayOneCardTwice() throws Exception
    {
        try (Lobby lobby = new Lobby(); ExecutorService executor = Executors.newFixedThreadPool(2))
        {
            Room room = runningRoom(lobby);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++)
            {
                int pileIndex = i;
                requests.add(executor.submit(() ->
                {
                    start.await();
                    return lobby.service.playHandCard("host", room.getId(), 101, pileIndex);
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
            assertEquals(1, room.getGameState().getBuildingPiles().get(0).size() + room.getGameState().getBuildingPiles().get(1).size());
            assertEquals(List.of(card(109, 9)), room.getGameState().getPlayers().getFirst().getHand());
        }
    }
}
