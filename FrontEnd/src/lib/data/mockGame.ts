import type { CardValue } from '#lib/data/cards.ts';

export type MockGamePlayer = {
    id: string;
    username: string;
    avatar: number;
    stockCount: number;
    stockTop: CardValue;
    handCount: number;
    discardTops: (CardValue | null)[];
    // Public discard cards, ordered from bottom to top. Stock has no contents array.
    discardPiles?: CardValue[][];
};

export type MockChatMessage = {
    id: string;
    playerId: string;
    username: string;
    avatar: number;
    message: string;
};

export const mockPlayers: MockGamePlayer[] = [
    { id: '3af-alex', username: 'Alex', avatar: 1, stockCount: 14, stockTop: 7, handCount: 5, discardTops: [3, 6, 10, null], discardPiles: [[2, 9, 4, 3], [8, 6], [5, 12, 10], []] },
    { id: '82c-anna', username: 'Anna', avatar: 2, stockCount: 18, stockTop: 4, handCount: 5, discardTops: [4, 9, 'WILD', 2], discardPiles: [[7, 3, 4], [2, 9], [6, 10, 'WILD'], [8, 5, 2]] },
    { id: '991-ben', username: 'Ben', avatar: 3, stockCount: 21, stockTop: 11, handCount: 3, discardTops: [2, null, 8, 6] },
    { id: 'c42-chris', username: 'Chris', avatar: 4, stockCount: 12, stockTop: 'WILD', handCount: 4, discardTops: [7, 12, null, 5] },
    { id: 'f17-dana', username: 'Dana', avatar: 5, stockCount: 16, stockTop: 9, handCount: 5, discardTops: [1, 3, 10, null] },
    { id: '6b2-eli', username: 'Eli', avatar: 6, stockCount: 24, stockTop: 2, handCount: 2, discardTops: [null, 5, 8, 'WILD'] }
];

export const mockHand: CardValue[] = [2, 5, 8, 'WILD', 12];
export const mockBuildingPiles: (CardValue | null)[] = [3, 5, 9, null];

export const mockChatMessages: MockChatMessage[] = [
    { id: 'preview-1', playerId: '82c-anna', username: 'Anna', avatar: 2, message: 'Who brought their lucky cards? 😎' },
    { id: 'preview-2', playerId: '3af-alex', username: 'Alex', avatar: 1, message: 'I brought snacks. Does that count?' }
];
