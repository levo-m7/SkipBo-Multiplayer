import type { CardValue } from '#lib/data/cards.ts';

export type GameCard = { id: number; type: 'NUMBER' | 'WILD'; value: number };

type GamePlayer = {
    playerId: string;
    playerNumber: number;
    username: string;
    avatar: number;
    stockTopCard: GameCard | null;
    stockCount: number;
};

export type LocalGamePlayer = GamePlayer & { hand: GameCard[]; discardPiles: GameCard[][] };
export type OpponentGamePlayer = GamePlayer & { handCount: number; discardTopCards: (GameCard | null)[] };
export type GameBuildingPile = { topCard: GameCard | null; size: number };
export type GameStateMessage = {
    type: 'GAME_STATE';
    roomId: string;
    playerCount: number;
    currentPlayerId: string;
    winnerPlayerId: string | null;
    drawPileCount: number;
    buildingPiles: GameBuildingPile[];
    localPlayer: LocalGamePlayer;
    opponents: OpponentGamePlayer[];
};

export type SelectedSource =
    | { type: 'HAND'; cardId: number }
    | { type: 'STOCK' }
    | { type: 'DISCARD'; discardPileIndex: number };

export type GameAction =
    | { type: 'PLAY_HAND_CARD'; cardId: number; buildingPileIndex: number }
    | { type: 'PLAY_STOCK_CARD'; buildingPileIndex: number }
    | { type: 'PLAY_DISCARD_CARD'; discardPileIndex: number; buildingPileIndex: number }
    | { type: 'DISCARD_HAND_CARD'; cardId: number; discardPileIndex: number };

export function cardValue(card: GameCard | null): CardValue | null {
    return card === null ? null : card.type === 'WILD' ? 'WILD' : card.value;
}

// Validate personalized wire data before allowing it to drive cards or actions.
export function isGameState(value: unknown): value is GameStateMessage {
    if (!value || typeof value !== 'object') return false;
    const state = value as GameStateMessage;
    const count = (n: unknown) => Number.isInteger(n) && Number(n) >= 0;
    const card = (c: GameCard | null): boolean => c === null || !!c && count(c.id)
        && (c.type === 'WILD' && c.value === 0 || c.type === 'NUMBER' && Number.isInteger(c.value) && c.value >= 1 && c.value <= 12);
    const player = (p: GamePlayer): boolean => !!p && typeof p.playerId === 'string' && p.playerId.length > 0
        && typeof p.username === 'string' && Number.isInteger(p.avatar) && p.avatar >= 1 && p.avatar <= 10
        && count(p.stockCount) && card(p.stockTopCard)
        && (Number.isInteger(p.playerNumber) && p.playerNumber >= 1 && p.playerNumber <= 6);
    if (state.type !== 'GAME_STATE' || typeof state.roomId !== 'string'
        || typeof state.currentPlayerId !== 'string' || !(state.winnerPlayerId === null || typeof state.winnerPlayerId === 'string')
        || !count(state.drawPileCount) || !Array.isArray(state.buildingPiles) || state.buildingPiles.length !== 4
        || !state.buildingPiles.every((p) => p && count(p.size) && card(p.topCard))
        || !player(state.localPlayer) || !Array.isArray(state.localPlayer.hand)
        || !state.localPlayer.hand.every((c) => c !== null && card(c))
        || !Array.isArray(state.localPlayer.discardPiles) || state.localPlayer.discardPiles.length !== 4
        || !state.localPlayer.discardPiles.every((pile) => Array.isArray(pile) && pile.every((c) => c !== null && card(c)))
        || !Array.isArray(state.opponents) || state.opponents.length < 1 || state.opponents.length > 5) return false;
    const opponentFields = new Set(['playerId', 'playerNumber', 'username', 'avatar', 'handCount', 'stockTopCard', 'stockCount', 'discardTopCards']);
    if (!state.opponents.every((p) => player(p) && count(p.handCount)
        && Array.isArray(p.discardTopCards) && p.discardTopCards.length === 4 && p.discardTopCards.every(card)
        && Object.keys(p).every((key) => opponentFields.has(key)))) return false;
    const players = [state.localPlayer, ...state.opponents];
    const ids = new Set(players.map((p) => p.playerId));
    const numbers = players.map((p) => p.playerNumber);
    return ids.size === players.length && ids.has(state.currentPlayerId)
        && (state.winnerPlayerId === null || ids.has(state.winnerPlayerId))
        && (state.playerCount === players.length)
        && new Set(numbers).size === numbers.length && numbers.every((number) => number <= players.length);
}
