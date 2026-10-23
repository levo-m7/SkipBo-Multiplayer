package org.skipbo.dto.websocket.game;

import org.skipbo.model.card.CardType;

public record GameCard(int id, CardType type, int value)
{
}
