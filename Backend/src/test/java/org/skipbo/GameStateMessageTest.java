package org.skipbo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.skipbo.dto.websocket.game.GameBuildingPile;
import org.skipbo.dto.websocket.game.GameCard;
import org.skipbo.dto.websocket.game.GameLocalPlayer;
import org.skipbo.dto.websocket.game.GameOpponent;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.Player;
import org.skipbo.model.card.Card;
import org.skipbo.model.card.CardType;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;
import org.skipbo.service.GameStateMessageFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class GameStateMessageTest
{
    private Card card(int id, int value)
    {
        return new Card(id, CardType.NUMBER, value);
    }

    private Room roomWithCards(Lobby lobby)
    {
        Room room = lobby.room(lobby.player("alex"), 4);
        for (String id : List.of("anna", "ben", "chris"))
        {
            lobby.service.joinRoom(lobby.player(id).getId(), room.getId());
        }
        int avatar = 1;
        for (String id : room.getPlayerIds())
        {
            lobby.players.findById(id).setUsername(id + "Name");
            lobby.players.findById(id).setAvatar(avatar);
            room.setReady(id, true);
            avatar++;
        }
        GameState game = new GameState(room.getPlayerIds());
        game.setCurrentPlayerId("ben");
        GamePlayerState alex = game.getPlayers().get(0);
        GamePlayerState anna = game.getPlayers().get(1);
        alex.getHand().addAll(List.of(card(9001, 3), new Card(1000, CardType.WILD, 0)));
        anna.getHand().add(card(9002, 7));
        game.getPlayers().get(2).getHand().add(card(9101, 8));
        game.getPlayers().get(3).getHand().add(card(9201, 9));
        alex.getStockPile().addAll(List.of(card(1001, 4), card(9006, 6)));
        anna.getStockPile().addAll(List.of(card(1002, 5), card(9004, 8)));
        for (int i = 0; i < 4; i++)
        {
            alex.getDiscardPiles().get(i).addAll(List.of(card(2000 + i, 2), card(2100 + i, 1)));
            anna.getDiscardPiles().get(i).addAll(List.of(card(3000 + i, 4), card(3100 + i, 3)));
        }
        game.getBuildingPiles().get(0).addAll(List.of(new Card(4001, CardType.WILD, 0), card(4002, 1)));
        game.getDrawPile().add(card(9003, 11));
        game.getCompletedCards().add(card(9005, 12));
        room.setGameState(game);
        room.startGame();
        return room;
    }

    private GameStateMessage view(Lobby lobby, Room room, String playerId)
    {
        return new GameStateMessageFactory(lobby.players).createGameStateMessage(room, playerId);
    }

    @Test
    void localHandContainsAllVisibleCardsIncludingWild()
    {
        try (Lobby lobby = new Lobby())
        {
            GameLocalPlayer local = view(lobby, roomWithCards(lobby), "alex").localPlayer();
            assertEquals(List.of(new GameCard(9001, CardType.NUMBER, 3), new GameCard(1000, CardType.WILD, 0)), local.hand());
        }
    }

    @Test
    void opponentJsonHasCountAndNoHandOrHiddenPileFields() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            GameStateMessage message = view(lobby, roomWithCards(lobby), "alex");
            GameOpponent anna = message.opponents().getFirst();
            assertEquals(1, anna.handCount());
            JsonNode json = JSON.getMapper().readTree(JSON.toJsonString(message, GameStateMessage.class));
            JsonNode opponent = json.get("opponents").get(0);
            assertEquals(8, opponent.size());
            for (String field : List.of("playerId", "playerNumber", "username", "avatar", "handCount", "stockTopCard", "stockCount", "discardTopCards"))
            {
                assertTrue(opponent.has(field));
            }
            assertFalse(opponent.has("hand"));
            assertFalse(opponent.has("stockPile"));
            assertFalse(opponent.has("discardPiles"));
        }
    }

    @Test
    void bothStockViewsContainOnlyTopAndCount()
    {
        try (Lobby lobby = new Lobby())
        {
            GameStateMessage message = view(lobby, roomWithCards(lobby), "alex");
            assertEquals(new GameCard(1001, CardType.NUMBER, 4), message.localPlayer().stockTopCard());
            assertEquals(2, message.localPlayer().stockCount());
            assertEquals(new GameCard(1002, CardType.NUMBER, 5), message.opponents().getFirst().stockTopCard());
            assertEquals(2, message.opponents().getFirst().stockCount());
        }
    }

    @Test
    void localDiscardsIncludeAllFourHistoriesInTopFirstOrder()
    {
        try (Lobby lobby = new Lobby())
        {
            GameLocalPlayer local = view(lobby, roomWithCards(lobby), "alex").localPlayer();
            assertEquals(4, local.discardPiles().size());
            for (int i = 0; i < 4; i++)
            {
                assertEquals(List.of(new GameCard(2000 + i, CardType.NUMBER, 2), new GameCard(2100 + i, CardType.NUMBER, 1)), local.discardPiles().get(i));
            }
        }
    }

    @Test
    void opponentDiscardsContainExactlyFourTopCards()
    {
        try (Lobby lobby = new Lobby())
        {
            GameOpponent anna = view(lobby, roomWithCards(lobby), "alex").opponents().getFirst();
            assertEquals(4, anna.discardTopCards().size());
            for (int i = 0; i < 4; i++)
            {
                assertEquals(new GameCard(3000 + i, CardType.NUMBER, 4), anna.discardTopCards().get(i));
            }
        }
    }

    @Test
    void secretCardIdsAndValuesAreAbsentFromActualSerializedMessage() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            GameStateMessage message = view(lobby, roomWithCards(lobby), "alex");
            String json = JSON.toJsonString(message, GameStateMessage.class);
            assertTrue(json.contains("9001"));
            assertTrue(json.contains("1002"));
            for (int secret : List.of(9002, 9003, 9004, 9005, 9006, 3100, 3101, 3102, 3103, 4002, 9101, 9201))
            {
                assertFalse(json.contains(Integer.toString(secret)), "Leaked secret card " + secret);
            }
            assertFalse(json.contains("\"value\":11"));
            assertFalse(json.contains("\"value\":12"));
            JsonNode tree = JSON.getMapper().readTree(json);
            assertEquals(9, tree.size());
            for (String field : List.of("type", "roomId", "playerCount", "currentPlayerId", "winnerPlayerId", "drawPileCount", "buildingPiles", "localPlayer", "opponents"))
            {
                assertTrue(tree.has(field));
            }
            assertEquals(8, tree.get("localPlayer").size());
            assertEquals(1, tree.get("localPlayer").get("playerNumber").asInt());
            assertEquals(4, tree.get("playerCount").asInt());
            assertFalse(json.contains("completedCards"));
            assertFalse(tree.has("drawPile"));
            assertFalse(tree.has("gameState"));
            assertEquals(1, message.drawPileCount());
        }
    }

    @Test
    void buildingPilesExposeOnlyTopAndSizeEvenWithWild() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            GameStateMessage message = view(lobby, roomWithCards(lobby), "alex");
            assertEquals(4, message.buildingPiles().size());
            assertEquals(new GameBuildingPile(new GameCard(4001, CardType.WILD, 0), 2), message.buildingPiles().get(0));
            for (int i = 1; i < 4; i++)
            {
                assertNull(message.buildingPiles().get(i).topCard());
                assertEquals(0, message.buildingPiles().get(i).size());
            }
            JsonNode json = JSON.getMapper().readTree(JSON.toJsonString(message, GameStateMessage.class));
            JsonNode pile = json.get("buildingPiles").get(0);
            assertEquals(2, pile.size());
            assertTrue(pile.has("topCard"));
            assertTrue(pile.has("size"));
        }
    }

    @Test
    void emptyPilesHavePredictableNullsCountsAndFourDiscardSlots() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            room.setGameState(new GameState(room.getPlayerIds()));
            GameStateMessage message = view(lobby, room, "alex");
            assertNull(message.localPlayer().stockTopCard());
            assertEquals(0, message.localPlayer().stockCount());
            assertTrue(message.localPlayer().hand().isEmpty());
            assertEquals(List.of(List.of(), List.of(), List.of(), List.of()), message.localPlayer().discardPiles());
            for (GameOpponent opponent : message.opponents())
            {
                assertEquals(0, opponent.handCount());
                assertEquals(0, opponent.stockCount());
                assertNull(opponent.stockTopCard());
                assertEquals(4, opponent.discardTopCards().size());
                for (GameCard top : opponent.discardTopCards())
                {
                    assertNull(top);
                }
            }
            JsonNode json = JSON.getMapper().readTree(JSON.toJsonString(message, GameStateMessage.class));
            assertTrue(json.get("localPlayer").get("stockTopCard").isNull());
            assertTrue(json.get("opponents").get(0).get("discardTopCards").get(0).isNull());
            assertTrue(json.get("buildingPiles").get(0).get("topCard").isNull());
            assertEquals(0, message.drawPileCount());
        }
    }

    @Test
    void roomTurnWinnerAndPlayerProfilesComeFromServerState()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            GameStateMessage message = view(lobby, room, "alex");
            assertEquals("GAME_STATE", message.type());
            assertEquals(room.getId(), message.roomId());
            assertEquals("ben", message.currentPlayerId());
            assertNull(message.winnerPlayerId());
            assertEquals("alex", message.localPlayer().playerId());
            assertEquals("alexName", message.localPlayer().username());
            assertEquals(1, message.localPlayer().avatar());
            assertEquals("annaName", message.opponents().get(0).username());
            assertEquals(2, message.opponents().get(0).avatar());
            assertEquals("benName", message.opponents().get(1).username());
            assertEquals(3, message.opponents().get(1).avatar());
            room.getGameState().setWinnerPlayerId("chris");
            lobby.players.findById("alex").setUsername("updated");
            assertEquals("chris", view(lobby, room, "alex").winnerPlayerId());
            assertEquals("updated", view(lobby, room, "alex").localPlayer().username());
        }
    }

    @Test
    void eachViewerGetsOwnHandAndOpponentsInOriginalOrder()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            GameStateMessage alex = view(lobby, room, "alex");
            GameStateMessage ben = view(lobby, room, "ben");
            assertEquals("alex", alex.localPlayer().playerId());
            assertEquals("ben", ben.localPlayer().playerId());
            assertEquals(9101, ben.localPlayer().hand().getFirst().id());
            for (int i = 0; i < 3; i++)
            {
                assertEquals(List.of("anna", "ben", "chris").get(i), alex.opponents().get(i).playerId());
                assertEquals(List.of("alex", "anna", "chris").get(i), ben.opponents().get(i).playerId());
            }
            assertNotEquals(alex.localPlayer(), ben.localPlayer());
        }
    }

    @Test
    void builderRejectsMissingGameAndViewersOutsideRoomOrGame()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            expectRejected(() -> view(lobby, room, "outsider"));
            expectRejected(() -> view(lobby, room, null));
            expectRejected(() -> view(lobby, null, "alex"));
            room.removePlayer("chris");
            expectRejected(() -> view(lobby, room, "chris"));
            room.setGameState(new GameState(List.of("alex", "ben")));
            expectRejected(() -> view(lobby, room, "anna"));
            room.setGameState(null);
            expectRejected(() -> view(lobby, room, "alex"));
        }
    }

    @Test
    void viewCopiesVisibleListsWithoutChangingGame()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            GameStateMessage message = view(lobby, room, "alex");
            GamePlayerState alex = room.getGameState().getPlayers().getFirst();
            assertEquals(2, alex.getHand().size());
            assertEquals(2, alex.getDiscardPiles().getFirst().size());
            message.localPlayer().hand().clear();
            message.localPlayer().discardPiles().getFirst().clear();
            assertEquals(2, alex.getHand().size());
            assertEquals(2, alex.getDiscardPiles().getFirst().size());
            assertEquals(1, room.getGameState().getDrawPile().size());
            assertEquals(1, room.getGameState().getCompletedCards().size());
        }
    }

    @Test
    void startGameSendsRoomStateThenPersonalizedGameStateToEachSocket() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player alex = lobby.player("alex");
            Player anna = lobby.player("anna");
            Room room = lobby.room(alex, 2);
            TestSocket alexSocket = lobby.connect(alex, room);
            TestSocket annaSocket = lobby.connect(anna, room);
            lobby.service.toggleReady("alex", room.getId());
            lobby.service.toggleReady("anna", room.getId());
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(annaSocket.message("{\"type\":\"START_GAME\",\"playerId\":\"alex\"}"));
            assertNull(room.getGameState());
            handler.onMessage(annaSocket.message("{\"type\":\"START_GAME\"}"));
            assertNull(room.getGameState());
            handler.onMessage(alexSocket.message("{\"type\":\"START_GAME\"}"));
            assertEquals(RoomState.IN_GAME, room.getState());
            for (TestSocket socket : List.of(alexSocket, annaSocket))
            {
                JsonNode notification = JSON.getMapper().readTree(socket.messages.get(socket.messages.size() - 2));
                assertEquals("ROOM_STATE", notification.get("type").asText());
                assertEquals("IN_GAME", notification.get("state").asText());
                assertFalse(notification.has("gameState"));
                GameStateMessage message = JSON.fromJsonString(socket.lastMessage(), GameStateMessage.class);
                assertEquals("GAME_STATE", message.type());
                assertEquals(socket.playerId, message.localPlayer().playerId());
                assertEquals(1, message.opponents().size());
                assertNotEquals(socket.playerId, message.opponents().getFirst().playerId());
                assertEquals(0, socket.closeCode);
            }
            GameStateMessage alexView = JSON.fromJsonString(alexSocket.lastMessage(), GameStateMessage.class);
            GameStateMessage annaView = JSON.fromJsonString(annaSocket.lastMessage(), GameStateMessage.class);
            assertEquals(5, alexView.localPlayer().hand().size());
            assertEquals(5, annaView.opponents().getFirst().handCount());
            assertTrue(annaView.localPlayer().hand().isEmpty());
            assertNotEquals(alexSocket.lastMessage(), annaSocket.lastMessage());
            for (Card card : room.getGameState().getPlayers().getFirst().getHand())
            {
                assertFalse(annaSocket.lastMessage().contains("\"id\":" + card.id() + ","));
            }
        }
    }

    @Test
    void reusableBroadcastSendsOnlyCurrentPlayersOwnViewsAndSkipsAbsentSockets() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            TestSocket alex = lobby.connect(lobby.players.findById("alex"), room);
            TestSocket anna = lobby.connect(lobby.players.findById("anna"), room);
            TestSocket staleAnna = anna;
            anna = lobby.connect(lobby.players.findById("anna"), room);
            int staleMessages = staleAnna.messages.size();
            lobby.service.broadcastGameState(room);
            assertEquals(staleMessages, staleAnna.messages.size());
            assertTrue(alex.lastMessage().contains("9001"));
            assertFalse(alex.lastMessage().contains("9002"));
            assertTrue(anna.lastMessage().contains("9002"));
            assertFalse(anna.lastMessage().contains("9001"));
            assertEquals(2, lobby.connections.getConnectionCount(room.getId()));
            assertEquals(4, room.getPlayerCount());
        }
    }

    @Test
    void failedGameSendDoesNotStopOtherPlayersOrRemoveInGameSeat() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = roomWithCards(lobby);
            TestSocket dead = lobby.connect(lobby.players.findById("alex"), room);
            TestSocket good = lobby.connect(lobby.players.findById("anna"), room);
            dead.failSend = true;
            lobby.service.broadcastGameState(room);
            assertEquals("GAME_STATE", JSON.getMapper().readTree(good.lastMessage()).get("type").asText());
            assertEquals(1, lobby.connections.getConnectionCount(room.getId()));
            lobby.scheduler.expireAll();
            assertTrue(room.hasPlayer("alex"));
            assertEquals(RoomState.IN_GAME, room.getState());
        }
    }

    @Test
    void waitingRoomDoesNotSendGameState() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Player alex = lobby.player("alex");
            Room room = lobby.room(alex, 2);
            TestSocket socket = lobby.connect(alex, room);
            int count = socket.messages.size();
            lobby.service.broadcastGameState(room);
            assertEquals(count, socket.messages.size());
        }
    }

    @Test
    void playerNumbersAndOpponentOrderAreGlobalForEveryViewer()
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = lobby.room(lobby.player("P1"), 4);
            for (String id : List.of("P2", "P3", "P4"))
            {
                lobby.service.joinRoom(lobby.player(id).getId(), room.getId());
            }
            for (String id : room.getPlayerIds())
            {
                lobby.service.toggleReady(id, room.getId());
            }
            lobby.service.startRoom("P1", room.getId());
            GameStateMessage p3 = view(lobby, room, "P3");
            assertEquals(3, p3.localPlayer().playerNumber());
            assertEquals("P1", p3.opponents().get(0).playerId());
            assertEquals(1, p3.opponents().get(0).playerNumber());
            assertEquals("P2", p3.opponents().get(1).playerId());
            assertEquals(2, p3.opponents().get(1).playerNumber());
            assertEquals("P4", p3.opponents().get(2).playerId());
            assertEquals(4, p3.opponents().get(2).playerNumber());

            int discardId = room.getGameState().getPlayers().getFirst().getHand().getFirst().id();
            assertTrue(lobby.service.discardHandCard("P1", room.getId(), discardId, 0));
            assertEquals("P2", room.getGameState().getCurrentPlayerId());

            for (int viewerIndex = 0; viewerIndex < 4; viewerIndex++)
            {
                GameStateMessage message = view(lobby, room, room.getPlayerIds().get(viewerIndex));
                assertEquals(viewerIndex + 1, message.localPlayer().playerNumber());
                assertEquals(4, message.playerCount());
                int opponentIndex = 0;
                for (int gameIndex = 0; gameIndex < 4; gameIndex++)
                {
                    if (gameIndex != viewerIndex)
                    {
                        GameOpponent opponent = message.opponents().get(opponentIndex);
                        assertEquals(room.getPlayerIds().get(gameIndex), opponent.playerId());
                        assertEquals(gameIndex + 1, opponent.playerNumber());
                        opponentIndex++;
                    }
                }
            }
        }
    }

}
