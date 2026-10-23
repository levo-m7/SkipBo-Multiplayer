package org.skipbo;

import org.junit.jupiter.api.Test;
import org.skipbo.model.card.Card;
import org.skipbo.model.card.CardType;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.service.GameService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameServiceTest
{
    private final GameService service = new GameService();

    private GameState game()
    {
        GameState game = new GameState(List.of("one", "two"));
        game.setCurrentPlayerId("one");
        return game;
    }

    private Card card(int value)
    {
        return new Card(value, CardType.NUMBER, value);
    }

    private void fillBuildingPile(GameState game, int count)
    {
        for (int value = 1; value <= count; value++)
        {
            game.getBuildingPiles().get(0).add(0, card(value));
        }
    }

    @Test
    void stockSizesAndDeckConservation()
    {
        for (int count = 2; count <= 6; count++)
        {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                ids.add("player" + i);
            }
            GameState game = service.createGame(ids);
            int expected = 30;
            if (count >= 5)
            {
                expected = 20;
            }
            List<Card> cards = new ArrayList<>(game.getDrawPile());
            for (GamePlayerState player : game.getPlayers())
            {
                assertEquals(expected, player.getStockPile().size());
                cards.addAll(player.getStockPile());
            }
            assertEquals(162, cards.size());
            assertEquals(162, new HashSet<>(cards).size());
            assertNull(game.getWinnerPlayerId());
        }
    }

    @Test
    void drawingFillsOnlyMissingCardsFromTheTop()
    {
        for (int existing = 0; existing <= 5; existing++)
        {
            GameState game = game();
            GamePlayerState player = game.getPlayers().get(0);
            for (int i = 0; i < existing; i++)
            {
                player.getHand().add(card(12));
            }
            for (int value = 1; value <= 6; value++)
            {
                game.getDrawPile().add(card(value));
            }
            service.drawCardsToFive(game, player);
            assertEquals(5, player.getHand().size());
            assertEquals(1 + existing, game.getDrawPile().size());
            assertEquals(card(6 - existing), game.getDrawPile().get(0));
        }
    }

    @Test
    void buildingRulesUseSizeEvenWhenTopIsWild()
    {
        List<Card> pile = new ArrayList<>();
        assertFalse(service.placeCardOnBuildingPile(card(2), pile));
        assertTrue(pile.isEmpty());
        assertTrue(service.placeCardOnBuildingPile(new Card(100, CardType.WILD, 0), pile));
        assertFalse(service.canPlayOnBuildingPile(card(1), pile));
        assertTrue(service.placeCardOnBuildingPile(card(2), pile));
        assertEquals(card(2), pile.get(0));
        assertFalse(service.canPlayOnBuildingPile(null, pile));
        for (int value = 3; value <= 12; value++)
        {
            assertTrue(service.placeCardOnBuildingPile(card(value), pile));
        }
        assertFalse(service.placeCardOnBuildingPile(new Card(101, CardType.WILD, 0), pile));
        assertEquals(12, pile.size());
    }

    @Test
    void handMoveValidatesOwnershipAndLegality()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().get(0);
        player.getHand().addAll(List.of(card(1), card(3)));
        assertFalse(service.playCardFromHand(game, "one", 99, 0));
        assertFalse(service.playCardFromHand(game, "one", 3, 0));
        assertEquals(List.of(card(1), card(3)), player.getHand());
        assertTrue(game.getBuildingPiles().get(0).isEmpty());
        assertTrue(service.playCardFromHand(game, "one", 1, 0));
        assertEquals(List.of(card(3)), player.getHand());
        assertEquals(List.of(card(1)), game.getBuildingPiles().get(0));
        assertEquals("one", game.getCurrentPlayerId());
    }

    @Test
    void stockAndDiscardOnlyPlayTheirTopCards()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().get(0);
        player.getStockPile().addAll(List.of(card(2), card(1)));
        List<Card> discard = player.getDiscardPiles().get(0);
        discard.addAll(List.of(card(2), card(1)));
        assertFalse(service.playCardFromStock(game, "one", 0));
        assertFalse(service.playCardFromDiscard(game, "one", 0, 0));
        assertEquals(List.of(card(2), card(1)), player.getStockPile());
        assertEquals(List.of(card(2), card(1)), discard);
        fillBuildingPile(game, 1);
        assertTrue(service.playCardFromStock(game, "one", 0));
        assertEquals(List.of(card(1)), player.getStockPile());
        assertNull(game.getWinnerPlayerId());
        game.getBuildingPiles().get(1).add(card(1));
        assertTrue(service.playCardFromDiscard(game, "one", 0, 1));
        assertEquals(List.of(card(1)), discard);
        assertEquals(card(2), game.getBuildingPiles().get(1).get(0));
    }

    @Test
    void discardingPutsCardOnTopAndStartsNextTurn()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().get(0);
        player.getHand().add(card(7));
        player.getDiscardPiles().get(2).add(card(9));
        game.getDrawPile().addAll(List.of(card(1), card(2), card(3), card(4), card(5)));
        assertFalse(service.discardCardFromHand(game, "one", 99, 2));
        assertEquals(List.of(card(7)), player.getHand());
        assertTrue(service.discardCardFromHand(game, "one", 7, 2));
        assertTrue(player.getHand().isEmpty());
        assertEquals(List.of(card(7), card(9)), player.getDiscardPiles().get(2));
        assertEquals("two", game.getCurrentPlayerId());
        assertEquals(5, game.getPlayers().get(1).getHand().size());
        service.nextTurn(game);
        assertEquals("one", game.getCurrentPlayerId());
    }

    @Test
    void everyBuildingMoveRecyclesExactlyTwelveCards()
    {
        for (int source = 0; source < 3; source++)
        {
            GameState game = game();
            fillBuildingPile(game, 11);
            service.recycleBuildingPileIfComplete(game, game.getBuildingPiles().get(0));
            assertEquals(11, game.getBuildingPiles().get(0).size());
            assertTrue(game.getCompletedCards().isEmpty());
            GamePlayerState player = game.getPlayers().get(0);
            boolean played;
            if (source == 0)
            {
                player.getHand().addAll(List.of(card(12), card(3)));
                played = service.playCardFromHand(game, "one", 12, 0);
            }
            else if (source == 1)
            {
                player.getStockPile().addAll(List.of(card(12), card(3)));
                played = service.playCardFromStock(game, "one", 0);
            }
            else
            {
                player.getDiscardPiles().get(0).add(card(12));
                played = service.playCardFromDiscard(game, "one", 0, 0);
            }
            assertTrue(played);
            assertTrue(game.getBuildingPiles().get(0).isEmpty());
            assertEquals(12, game.getCompletedCards().size());
            for (int i = 0; i < 12; i++)
            {
                assertEquals(card(12 - i), game.getCompletedCards().get(i));
            }
        }
    }

    @Test
    void drawRefillsFromCompletedCardsAndPreservesEveryCard()
    {
        GameState game = game();
        fillBuildingPile(game, 12);
        service.recycleBuildingPileIfComplete(game, game.getBuildingPiles().get(0));
        List<Card> recycled = new ArrayList<>(game.getCompletedCards());
        game.getDrawPile().add(new Card(99, CardType.NUMBER, 1));
        GamePlayerState player = game.getPlayers().get(0);
        service.drawCardsToFive(game, player);
        assertEquals(5, player.getHand().size());
        assertEquals(99, player.getHand().get(0).id());
        assertEquals(8, game.getDrawPile().size());
        assertTrue(game.getCompletedCards().isEmpty());
        List<Card> remaining = new ArrayList<>(player.getHand());
        remaining.remove(0);
        remaining.addAll(game.getDrawPile());
        assertEquals(12, remaining.size());
        assertEquals(new HashSet<>(recycled), new HashSet<>(remaining));
    }

    @Test
    void drawingStopsWhenAllAvailableCardsAreExhausted()
    {
        GameState game = game();
        game.getCompletedCards().add(card(1));
        service.drawCardsToFive(game, game.getPlayers().get(0));
        assertEquals(List.of(card(1)), game.getPlayers().get(0).getHand());
        assertTrue(game.getDrawPile().isEmpty());
        assertTrue(game.getCompletedCards().isEmpty());
    }

    @Test
    void finalStockCardWinsImmediatelyIncludingWhenBuildingPileCompletes()
    {
        for (int value : List.of(5, 12))
        {
            GameState game = game();
            fillBuildingPile(game, value - 1);
            game.getPlayers().get(0).getStockPile().add(card(value));
            assertTrue(service.playCardFromStock(game, "one", 0));
            assertTrue(game.getPlayers().get(0).getStockPile().isEmpty());
            assertEquals("one", game.getWinnerPlayerId());
            assertEquals("one", game.getCurrentPlayerId());
            if (value == 12)
            {
                assertTrue(game.getBuildingPiles().get(0).isEmpty());
                assertEquals(12, game.getCompletedCards().size());
            }
        }
    }

    @Test
    void winnerBlocksMovesTurnsAndDrawing()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().get(0);
        player.getHand().add(card(1));
        player.getStockPile().add(card(1));
        player.getDiscardPiles().get(0).add(card(1));
        game.getCompletedCards().add(card(3));
        game.getDrawPile().add(card(2));
        game.setWinnerPlayerId("two");
        assertFalse(service.playCardFromHand(game, "one", 1, 0));
        assertFalse(service.playCardFromStock(game, "one", 0));
        assertFalse(service.playCardFromDiscard(game, "one", 0, 0));
        assertFalse(service.discardCardFromHand(game, "one", 1, 0));
        service.startTurn(game);
        service.nextTurn(game);
        service.drawCardsToFive(game, player);
        assertEquals("one", game.getCurrentPlayerId());
        assertEquals("two", game.getWinnerPlayerId());
        assertEquals(List.of(card(1)), player.getHand());
        assertEquals(List.of(card(1)), player.getStockPile());
        assertEquals(List.of(card(1)), player.getDiscardPiles().get(0));
        assertEquals(List.of(card(2)), game.getDrawPile());
        assertEquals(List.of(card(3)), game.getCompletedCards());
        assertTrue(game.getBuildingPiles().get(0).isEmpty());
    }

    @Test
    void invalidIndexesDoNotMutateState()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().get(0);
        player.getHand().add(card(1));
        player.getStockPile().add(card(1));
        player.getDiscardPiles().get(0).add(card(1));
        for (int index : List.of(-1, 4))
        {
            assertFalse(service.playCardFromHand(game, "one", 1, index));
            assertFalse(service.playCardFromStock(game, "one", index));
            assertFalse(service.playCardFromDiscard(game, "one", 0, index));
            assertFalse(service.playCardFromDiscard(game, "one", index, 0));
            assertFalse(service.discardCardFromHand(game, "one", 1, index));
        }
        assertEquals(List.of(card(1)), player.getHand());
        assertEquals(List.of(card(1)), player.getStockPile());
        assertEquals(List.of(card(1)), player.getDiscardPiles().get(0));
        assertEquals("one", game.getCurrentPlayerId());
        for (List<Card> pile : game.getBuildingPiles())
        {
            assertTrue(pile.isEmpty());
        }
    }

    @Test
    void wrongTurnNullAndUnknownPlayersCannotAct()
    {
        for (String id : new String[]{"two", null, "unknown"})
        {
            GameState game = game();
            for (GamePlayerState player : game.getPlayers())
            {
                player.getHand().add(card(1));
                player.getStockPile().add(card(1));
                player.getDiscardPiles().get(0).add(card(1));
            }
            if ("unknown".equals(id))
            {
                game.setCurrentPlayerId(id);
            }
            assertFalse(service.playCardFromHand(game, id, 1, 0));
            assertFalse(service.playCardFromStock(game, id, 0));
            assertFalse(service.playCardFromDiscard(game, id, 0, 0));
            assertFalse(service.discardCardFromHand(game, id, 1, 0));
            for (GamePlayerState player : game.getPlayers())
            {
                assertEquals(List.of(card(1)), player.getHand());
                assertEquals(List.of(card(1)), player.getStockPile());
                assertEquals(List.of(card(1)), player.getDiscardPiles().get(0));
            }
            assertTrue(game.getBuildingPiles().get(0).isEmpty());
        }
    }
    @Test
    void setupCreatesFourPilesAndStartsFirstPlayersTurn()
    {
        GameState game = service.createGame(List.of("one", "two", "three"));
        assertEquals(4, game.getBuildingPiles().size());
        assertEquals("one", game.getCurrentPlayerId());
        for (GamePlayerState player : game.getPlayers())
        {
            assertEquals(4, player.getDiscardPiles().size());
        }
        service.startTurn(game);
        assertEquals(5, game.getPlayers().getFirst().getHand().size());
        assertTrue(game.getPlayers().get(1).getHand().isEmpty());
    }

    @Test
    void emptyHandRefillsRepeatedlyDuringSameTurn()
    {
        GameState game = game();
        GamePlayerState player = game.getPlayers().getFirst();
        player.getHand().add(card(1));
        for (int value = 2; value <= 11; value++)
        {
            game.getDrawPile().add(card(value));
        }
        assertTrue(service.playCardFromHand(game, "one", 1, 0));
        assertEquals(List.of(card(2), card(3), card(4), card(5), card(6)), player.getHand());
        assertEquals("one", game.getCurrentPlayerId());
        for (int value = 2; value <= 6; value++)
        {
            assertTrue(service.playCardFromHand(game, "one", value, 0));
        }
        assertEquals(List.of(card(7), card(8), card(9), card(10), card(11)), player.getHand());
        assertEquals("one", game.getCurrentPlayerId());
        assertTrue(game.getDrawPile().isEmpty());
    }

    @Test
    void finalHandCardCanRecycleBuildingAndImmediatelyDrawFromIt()
    {
        GameState game = game();
        fillBuildingPile(game, 11);
        GamePlayerState player = game.getPlayers().getFirst();
        player.getHand().add(card(12));
        assertTrue(service.playCardFromHand(game, "one", 12, 0));
        assertTrue(game.getBuildingPiles().get(0).isEmpty());
        assertEquals(5, player.getHand().size());
        assertEquals(7, game.getDrawPile().size());
        assertTrue(game.getCompletedCards().isEmpty());
        assertEquals("one", game.getCurrentPlayerId());
    }

    @Test
    void endingDiscardDoesNotRefillOldHandEvenWhenEnoughCardsRemain()
    {
        GameState game = game();
        game.getPlayers().getFirst().getHand().add(card(9));
        for (int i = 0; i < 10; i++)
        {
            game.getDrawPile().add(new Card(100 + i, CardType.NUMBER, 1));
        }
        assertTrue(service.discardCardFromHand(game, "one", 9, 0));
        assertTrue(game.getPlayers().getFirst().getHand().isEmpty());
        assertEquals(5, game.getPlayers().get(1).getHand().size());
        assertEquals(5, game.getDrawPile().size());
        assertEquals("two", game.getCurrentPlayerId());
    }

    private List<Card> allCards(GameState game)
    {
        List<Card> cards = new ArrayList<>(game.getDrawPile());
        cards.addAll(game.getCompletedCards());
        for (List<Card> pile : game.getBuildingPiles())
        {
            cards.addAll(pile);
        }
        for (GamePlayerState player : game.getPlayers())
        {
            cards.addAll(player.getHand());
            cards.addAll(player.getStockPile());
            for (List<Card> pile : player.getDiscardPiles())
            {
                cards.addAll(pile);
            }
        }
        return cards;
    }

    private void assertCardsConserved(GameState game, HashSet<Card> original)
    {
        List<Card> cards = allCards(game);
        assertEquals(162, cards.size());
        assertEquals(original, new HashSet<>(cards));
        HashSet<Integer> ids = new HashSet<>();
        for (Card card : cards)
        {
            assertTrue(ids.add(card.id()), "Duplicate card ID " + card.id());
        }
    }

    @Test
    void all162CardsSurviveMovesCompletionRecyclingAndTurnChanges()
    {
        GameState game = service.createGame(List.of("one", "two"));
        HashSet<Card> original = new HashSet<>(allCards(game));
        GamePlayerState player = game.getPlayers().getFirst();
        for (int value = 1; value <= 12; value++)
        {
            Card selected = null;
            for (Card card : original)
            {
                if (card.type() == CardType.NUMBER && card.value() == value)
                {
                    selected = card;
                    break;
                }
            }
            game.getDrawPile().remove(selected);
            for (GamePlayerState owner : game.getPlayers())
            {
                owner.getStockPile().remove(selected);
            }
            assertNotNull(selected);
            player.getHand().add(selected);
        }
        player.getHand().add(game.getDrawPile().remove(0));
        assertCardsConserved(game, original);
        for (int value = 1; value <= 12; value++)
        {
            int cardId = player.getHand().getFirst().id();
            assertTrue(service.playCardFromHand(game, "one", cardId, 0));
            assertCardsConserved(game, original);
        }
        assertEquals(12, game.getCompletedCards().size());
        assertTrue(game.getBuildingPiles().get(0).isEmpty());
        game.getPlayers().get(1).getStockPile().addAll(game.getDrawPile());
        game.getDrawPile().clear();
        int discardId = player.getHand().getFirst().id();
        assertTrue(service.discardCardFromHand(game, "one", discardId, 2));
        assertEquals(5, game.getPlayers().get(1).getHand().size());
        assertEquals(7, game.getDrawPile().size());
        assertTrue(game.getCompletedCards().isEmpty());
        assertCardsConserved(game, original);
    }

}
