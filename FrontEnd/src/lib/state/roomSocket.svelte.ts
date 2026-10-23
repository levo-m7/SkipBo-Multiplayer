import { playGameSound } from '#lib/audio/gameAudio.ts';
import { isGameState, type GameStateMessage, type GameAction, type GameCard } from '#lib/types/game.ts';
import { goto } from '$app/navigation';
import { getPlayer } from '#lib/api/player.ts';
import { store } from '#lib/state/store.svelte.ts';

export type LobbyPlayer = {
    id: string;
    username: string;
    avatar: number;
    ready: boolean;
    host: boolean;
};

export type ChatMessage = {
    id: string;
    playerId: string;
    username: string;
    avatar: number;
    message: string;
};

export type RoomState = {
    type: 'ROOM_STATE';
    roomId: string;
    roomCode: string;
    roomName: string;
    playerTarget: number;
    state: string;
    players: LobbyPlayer[];
    chatMessages: ChatMessage[];
};

type RoomSocketStore = {
    connected: boolean;
    reconnecting: boolean;
    unavailable: boolean;
    readyPending: boolean;
    leavePending: boolean;
    room: RoomState | null;
    game: GameStateMessage | null;
    gamePending: boolean;
    startPending: boolean;
    gameRevision: number;
    notice: { title: string; reason?: string } | null;
    error: string | null;
};

export const roomSocketStore = $state<RoomSocketStore>({
    connected: false,
    reconnecting: false,
    unavailable: false,
    readyPending: false,
    leavePending: false,
    room: null,
    game: null,
    gamePending: false,
    startPending: false,
    gameRevision: 0,
    notice: null,
    error: null
});

let socket: WebSocket | null = null;
let activeRoomId: string | null = null;
let pendingAudio: { action: GameAction; card: GameCard } | null = null;
let releaseTimer: ReturnType<typeof setTimeout> | undefined;
let noticeTimer: ReturnType<typeof setTimeout> | undefined;
let missingRoomTimer: ReturnType<typeof setTimeout> | undefined;
let startTimer: ReturnType<typeof setTimeout> | undefined;
export function releaseRoomConnection() {
    clearTimeout(releaseTimer);
    releaseTimer = setTimeout(disconnectFromRoom, 0);
}

function clearGamePending() {
    pendingAudio = null;
    roomSocketStore.gamePending = false;
}

function playConfirmedMoveSound(previous: GameStateMessage, next: GameStateMessage) {
    if (!pendingAudio) return;
    const { action, card } = pendingAudio;
    const player = next.localPlayer;
    let confirmed = false;
    if (action.type === 'DISCARD_HAND_CARD') {
        confirmed = !player.hand.some((item) => item.id === card.id)
            && player.discardPiles[action.discardPileIndex]?.[0]?.id === card.id;
    } else if (action.type === 'PLAY_HAND_CARD') {
        confirmed = !player.hand.some((item) => item.id === card.id);
    } else if (action.type === 'PLAY_STOCK_CARD') {
        confirmed = player.stockCount < previous.localPlayer.stockCount && player.stockTopCard?.id !== card.id;
    } else {
        confirmed = player.discardPiles[action.discardPileIndex].length < previous.localPlayer.discardPiles[action.discardPileIndex].length
            && player.discardPiles[action.discardPileIndex][0]?.id !== card.id;
    }
    if (confirmed) playGameSound(card.type === 'WILD' && action.type !== 'DISCARD_HAND_CARD' ? 'wild' : 'card');
}

function playOpponentMoveSound(previous: GameStateMessage, next: GameStateMessage) {
    const before = previous.opponents.find((player) => player.playerId === previous.currentPlayerId);
    const after = next.opponents.find((player) => player.playerId === before?.playerId);
    if (!before || !after || previous.roomId !== next.roomId) return;

    const stockPlayed = before.stockTopCard && after.stockCount < before.stockCount
        && after.stockTopCard?.id !== before.stockTopCard.id ? before.stockTopCard : null;
    const discardPlayed = before.discardTopCards.filter((card, index) => card && after.discardTopCards[index]?.id !== card.id);
    const buildingChanged = next.buildingPiles.some((pile, index) =>
        pile.size !== previous.buildingPiles[index].size || pile.topCard?.id !== previous.buildingPiles[index].topCard?.id);
    let played: GameCard | null = null;
    if (buildingChanged) {
        const destination = next.buildingPiles.find((pile, index) => pile.topCard
            && pile.topCard.id !== previous.buildingPiles[index].topCard?.id
            && pile.size > previous.buildingPiles[index].size);
        if (destination?.topCard && (before.handCount !== after.handCount
            || next.drawPileCount < previous.drawPileCount
            || stockPlayed?.id === destination.topCard.id
            || discardPlayed.some((card) => card?.id === destination.topCard?.id))) {
            played = destination.topCard;
        } else if (next.buildingPiles.some((pile, index) => pile.topCard === null && pile.size === 0
            && previous.buildingPiles[index].size > 0)) {
            played = stockPlayed ?? (discardPlayed.length === 1 ? discardPlayed[0] : null);
        }
    } else if (after.handCount < before.handCount) {
        played = after.discardTopCards.find((card, index) => card && card.id !== before.discardTopCards[index]?.id) ?? null;
    }
    if (played) playGameSound(played.type === 'WILD' && buildingChanged ? 'wild' : 'card', 'opponentCards');
}

const rejectionReasons: Record<string, string> = {
    NOT_YOUR_TURN: 'NOT YOUR TURN',
    INVALID_CARD: 'CARD NOT AVAILABLE',
    INVALID_PILE: 'INVALID PILE',
    ILLEGAL_MOVE: 'THAT MOVE IS NOT ALLOWED',
    GAME_ALREADY_FINISHED: 'GAME IS OVER'
};

function showMoveRejected(reason: unknown) {
    clearGamePending();
    roomSocketStore.leavePending = false;
    clearTimeout(noticeTimer);
    roomSocketStore.error = null;
    roomSocketStore.notice = { title: 'MOVE REJECTED', reason: typeof reason === 'string' && Object.hasOwn(rejectionReasons, reason) ? rejectionReasons[reason] : undefined };
    noticeTimer = setTimeout(() => { roomSocketStore.notice = null; }, 1800);
}

function roomNoLongerAvailable() {
    disconnectFromRoom();
    roomSocketStore.notice = { title: 'GAME NO LONGER AVAILABLE' };
    missingRoomTimer = setTimeout(() => {
        roomSocketStore.notice = null;
        void goto('/');
    }, 1000);
}

function acceptConnectionState() {
    receivedRoomState = true;
    clearTimeout(connectionTimer);
    connectionTimer = undefined;
    reconnectDelay = 1000;
    reconnectAttempts = 0;
    roomSocketStore.reconnecting = false;
}

let connectionVersion = 0;
let intentionalDisconnect = true;
let receivedRoomState = false;
let reconnectDelay = 1000;
let reconnectAttempts = 0;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let connectionTimer: ReturnType<typeof setTimeout> | undefined;
let readyTimer: ReturnType<typeof setTimeout> | undefined;
let leaveTimer: ReturnType<typeof setTimeout> | undefined;

export async function connectToRoom(roomId: string) {
    clearTimeout(releaseTimer);
    releaseTimer = undefined;
    if (activeRoomId === roomId && !intentionalDisconnect && !roomSocketStore.unavailable) return;
    disconnectFromRoom();
    activeRoomId = roomId;
    intentionalDisconnect = false;
    await openRoomSocket(roomId);
}

function reconnectToRoom(roomId: string) {
    if (intentionalDisconnect || roomSocketStore.unavailable || reconnectTimer !== undefined) return;
    if (reconnectAttempts >= 20) {
        stopRoomConnection('COULD NOT CONNECT TO ROOM');
        return;
    }
    roomSocketStore.reconnecting = true;
    roomSocketStore.error = 'CONNECTION LOST — RECONNECTING...';
    reconnectTimer = setTimeout(() => {
        reconnectTimer = undefined;
        reconnectAttempts += 1;
        void openRoomSocket(roomId);
    }, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, 8000);
}

function stopRoomConnection(message: string) {
    disconnectFromRoom();
    roomSocketStore.unavailable = true;
    roomSocketStore.error = message;
}

async function openRoomSocket(roomId: string) {
    const version = ++connectionVersion;
    let connection: WebSocket;
    receivedRoomState = false;
    let receivedGameState = false;
    let seenChatIds: Set<string> | null = null;

    connectionTimer = setTimeout(() => {
        if (version !== connectionVersion) return;
        connectionVersion += 1;
        const connection = socket;
        socket = null;
        connection?.close();
        connectionTimer = undefined;
        roomSocketStore.connected = false;
        clearGamePending();
        clearTimeout(startTimer);
        roomSocketStore.startPending = false;
        reconnectToRoom(roomId);
    }, 10000);

    try {
        const player = await getPlayer();
        if (version !== connectionVersion || intentionalDisconnect) return;
        store.player = player;
        const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
        connection = new WebSocket(
            `${protocol}://${window.location.host}/ws/rooms/${encodeURIComponent(roomId)}`
        );
    } catch (error) {
        if (version !== connectionVersion) return;
        console.error('[Room WS] connection failed', error);
        clearTimeout(connectionTimer);
        connectionTimer = undefined;
        reconnectToRoom(roomId);
        return;
    }
    socket = connection;

    connection.onopen = () => {
        if (socket !== connection) return;
        roomSocketStore.connected = true;
    };

    connection.onmessage = (event) => {
        if (socket !== connection) return;
        try {
            const message = JSON.parse(event.data);
            if (message?.type === 'ROOM_NOT_FOUND') {
                roomNoLongerAvailable();
                return;
            }
            if (message?.type === 'GAME_ACTION_REJECTED') {
                showMoveRejected(message.reason);
                return;
            }
            if (message?.type === 'LEFT_ROOM') {
                disconnectFromRoom();
                void goto('/');
                return;
            }
            if (message?.type === 'GAME_STATE') {
                if (!isGameState(message) || message.roomId !== roomId || message.localPlayer.playerId !== store.player.id) {
                    throw new Error('Invalid personalized game state');
                }
                const previous = roomSocketStore.game;
                if (receivedGameState && previous) {
                    if (pendingAudio || previous.currentPlayerId === previous.localPlayer.playerId) {
                        playConfirmedMoveSound(previous, message);
                    } else {
                        playOpponentMoveSound(previous, message);
                    }
                    if (previous.currentPlayerId !== message.currentPlayerId) {
                        playGameSound('turn', message.currentPlayerId === message.localPlayer.playerId ? 'myTurn' : 'turnSwitched');
                    }
                }
                receivedGameState = true;
                acceptConnectionState();
                roomSocketStore.game = message;
                roomSocketStore.gameRevision += 1;
                clearGamePending();
                clearTimeout(startTimer);
                roomSocketStore.startPending = false;
                roomSocketStore.error = null;
                return;
            }
            if (message?.type === 'ERROR' || message?.type === 'GAME_ERROR') {
                clearGamePending();
                clearTimeout(startTimer);
                roomSocketStore.startPending = false;
                roomSocketStore.error = 'ACTION COULD NOT BE COMPLETED';
                return;
            }
            if (message?.type === 'ROOM_STATE') {
                if (message.roomId !== roomId || typeof message.roomCode !== 'string'
                    || typeof message.roomName !== 'string' || typeof message.state !== 'string'
                    || !Number.isInteger(message.playerTarget)
                    || !Array.isArray(message.players) || !Array.isArray(message.chatMessages)) {
                    throw new Error('Invalid room state');
                }
                const validPlayers = message.players.every((player: LobbyPlayer) =>
                    player && typeof player.id === 'string' && typeof player.username === 'string'
                    && Number.isInteger(player.avatar) && typeof player.ready === 'boolean'
                    && typeof player.host === 'boolean'
                );
                const validChat = message.chatMessages.every((chat: ChatMessage) =>
                    chat && typeof chat.id === 'string' && typeof chat.playerId === 'string'
                    && typeof chat.username === 'string' && Number.isInteger(chat.avatar)
                    && typeof chat.message === 'string'
                );
                const uniquePlayers = new Set(message.players.map((player: LobbyPlayer) => player?.id));
                const uniqueMessages = new Set(message.chatMessages.map((chat: ChatMessage) => chat?.id));
                if (!validPlayers || !validChat || uniquePlayers.size !== message.players.length
                    || uniqueMessages.size !== message.chatMessages.length) {
                    throw new Error('Invalid room state');
                }
                if (message.state === 'CLOSED') {
                    stopRoomConnection('ROOM EXPIRED');
                    return;
                }
                const currentPlayer = message.players.find((player: LobbyPlayer) => player.id === store.player.id);
                if (!currentPlayer) {
                    stopRoomConnection('YOU ARE NO LONGER IN THIS ROOM');
                    return;
                }
                if (message.state !== 'IN_GAME' || receivedGameState) acceptConnectionState();
                else receivedRoomState = true;
                roomSocketStore.readyPending = false;
                clearTimeout(readyTimer);
                readyTimer = undefined;
                if (seenChatIds && receivedGameState && message.state === 'IN_GAME'
                    && message.chatMessages.some((chat: ChatMessage) => !seenChatIds!.has(chat.id) && chat.playerId !== store.player.id)) {
                    playGameSound('chat');
                }
                seenChatIds ??= new Set();
                message.chatMessages.forEach((chat: ChatMessage) => seenChatIds!.add(chat.id));
                roomSocketStore.room = message;
                if (message.state === 'WAITING') {
                    roomSocketStore.game = null;
                    receivedGameState = false;
                    clearGamePending();
                    roomSocketStore.leavePending = false;
                    clearTimeout(leaveTimer);
                }
                if (message.state === 'IN_GAME') {
                    if (!receivedGameState) roomSocketStore.reconnecting = true;
                    clearTimeout(startTimer);
                    roomSocketStore.startPending = false;
                }
                roomSocketStore.error = null;

                store.player.username = currentPlayer.username;
                store.player.avatar = currentPlayer.avatar;
            }
        } catch {
            clearGamePending();
            roomSocketStore.error = 'Could not read room update';
        }
    };

    connection.onclose = (event) => {
        console.warn('[Room WS] closed', {
            roomId,
            code: event.code,
            reason: event.reason,
            wasClean: event.wasClean,
            currentConnection: socket === connection
        });
        if (socket !== connection) return;
        const wasLeaving = roomSocketStore.leavePending && roomSocketStore.room?.state !== 'IN_GAME';
        clearGamePending();
        clearTimeout(startTimer);
        roomSocketStore.startPending = false;
        roomSocketStore.connected = false;
        socket = null;
        receivedRoomState = false;
        clearTimeout(connectionTimer);
        connectionTimer = undefined;
        clearTimeout(readyTimer);
        readyTimer = undefined;
        clearTimeout(leaveTimer);
        leaveTimer = undefined;
        roomSocketStore.readyPending = false;
        roomSocketStore.leavePending = false;
        const reason = event.reason.toLowerCase();
        if (wasLeaving) {
            stopRoomConnection('CONNECTION LOST WHILE LEAVING');
        } else if (event.code === 4004 || /room.*(not found|expired|closed|invalid|gone|does not exist|no longer exists)|invalid room/.test(reason)) {
            stopRoomConnection('ROOM EXPIRED');
        } else if (/full/.test(reason)) {
            stopRoomConnection('ROOM IS FULL');
        } else if (event.code === 4001 || event.code === 4009 || event.code === 1008
            || /player.*(removed|kicked|not in|timeout|timed out)|invalid session/.test(reason)) {
            stopRoomConnection('COULD NOT JOIN ROOM');
        } else {
            reconnectToRoom(roomId);
        }
    };

    connection.onerror = (event) => {
        if (socket !== connection) return;
        clearGamePending();
        console.error('[Room WS] error', { roomId, readyState: connection.readyState, event });
        roomSocketStore.error = 'CONNECTION LOST — RECONNECTING...';
    };
}

function sendRoomMessage(message: { type: string; username?: string; avatar?: number; message?: string } | GameAction): boolean {
    if (socket === null || socket.readyState !== WebSocket.OPEN || !receivedRoomState
        || roomSocketStore.leavePending) return false;
    try {
        socket.send(JSON.stringify(message));
        return true;
    } catch (error) {
        console.error('[Room WS] send failed', { type: message.type, error });
        roomSocketStore.error = 'COULD NOT SEND — TRY AGAIN';
        return false;
    }
}

export function startGame() {
    if (roomSocketStore.startPending || !sendRoomMessage({ type: 'START_GAME' })) return;
    roomSocketStore.startPending = true;
    startTimer = setTimeout(() => {
        roomSocketStore.startPending = false;
        roomSocketStore.error = 'NO START CONFIRMATION — TRY AGAIN';
    }, 8000);
}

export function sendGameAction(action: GameAction): boolean {
    const game = roomSocketStore.game;
    if (!game || game.winnerPlayerId !== null || game.currentPlayerId !== game.localPlayer.playerId
        || !roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.gamePending) return false;
    if (!sendRoomMessage(action)) return false;
    const player = game.localPlayer;
    const card = action.type === 'PLAY_STOCK_CARD' ? player.stockTopCard
        : action.type === 'PLAY_DISCARD_CARD' ? player.discardPiles[action.discardPileIndex]?.[0]
        : player.hand.find((item) => item.id === action.cardId);
    pendingAudio = card ? { action, card } : null;
    roomSocketStore.gamePending = true;
    roomSocketStore.error = null;
    return true;
}

export function updateRoomUsername(username: string) {
    const player = roomSocketStore.room?.players.find((player) => player.id === store.player.id);
    if (!player || player.ready || !/^[a-zA-Z0-9_-]{1,8}$/.test(username)) return;
    sendRoomMessage({ type: 'UPDATE_USERNAME', username });
}

export function updateRoomAvatar(avatar: number) {
    const player = roomSocketStore.room?.players.find((player) => player.id === store.player.id);
    if (!player || player.ready || !Number.isInteger(avatar) || avatar < 1 || avatar > 10) return;
    sendRoomMessage({ type: 'UPDATE_AVATAR', avatar });
}

export function toggleRoomReady() {
    if (roomSocketStore.readyPending) return;
    if (!sendRoomMessage({ type: 'TOGGLE_READY' })) return;
    roomSocketStore.readyPending = true;
    readyTimer = setTimeout(() => {
        readyTimer = undefined;
        roomSocketStore.readyPending = false;
        roomSocketStore.error = 'NO READY UPDATE YET — TRY AGAIN';
    }, 8000);
}

export function sendChatMessage(message: string): boolean {
    const text = message.trim();
    if (!text || text.length > 200) return false;
    return sendRoomMessage({ type: 'CHAT_MESSAGE', message: text });
}

export function leaveGame(): boolean {
    if (roomSocketStore.room?.state !== 'IN_GAME' || roomSocketStore.reconnecting
        || !sendRoomMessage({ type: 'LEAVE_GAME' })) return false;
    roomSocketStore.leavePending = true;
    return true;
}

export function leaveRoom() {
    if (!sendRoomMessage({ type: 'LEAVE_ROOM' })) return;
    roomSocketStore.leavePending = true;
    leaveTimer = setTimeout(() => {
        leaveTimer = undefined;
        roomSocketStore.leavePending = false;
        roomSocketStore.error = 'COULD NOT LEAVE — TRY AGAIN';
    }, 8000);
}

export function disconnectFromRoom() {
    intentionalDisconnect = true;
    activeRoomId = null;
    clearTimeout(noticeTimer);
    clearTimeout(missingRoomTimer);
    noticeTimer = undefined;
    missingRoomTimer = undefined;
    roomSocketStore.notice = null;
    clearTimeout(releaseTimer);
    releaseTimer = undefined;
    clearGamePending();
    clearTimeout(startTimer);
    startTimer = undefined;
    roomSocketStore.startPending = false;
    roomSocketStore.game = null;
    connectionVersion += 1;
    clearTimeout(reconnectTimer);
    clearTimeout(connectionTimer);
    clearTimeout(readyTimer);
    clearTimeout(leaveTimer);
    reconnectTimer = undefined;
    connectionTimer = undefined;
    readyTimer = undefined;
    leaveTimer = undefined;
    reconnectDelay = 1000;
    reconnectAttempts = 0;
    receivedRoomState = false;
    const connection = socket;
    socket = null;
    connection?.close();
    roomSocketStore.connected = false;
    roomSocketStore.reconnecting = false;
    roomSocketStore.unavailable = false;
    roomSocketStore.readyPending = false;
    roomSocketStore.leavePending = false;
    roomSocketStore.room = null;
    roomSocketStore.error = null;
}
