export type GlobalRoom = {
	roomId: string;
	roomCode: string;
	roomName: string;
	playerCount: number;
	playerTarget: number;
	state: string;
	full: boolean;
};

type GlobalRoomStore = {
	connected: boolean;
	rooms: GlobalRoom[];
	error: string | null;
};

export const globalRoomsStore = $state<GlobalRoomStore>({
	connected: false,
	rooms: [],
	error: null
});

let socket: WebSocket | null = null;
let active = false;
let reconnectDelay = 1000;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let connectionTimer: ReturnType<typeof setTimeout> | undefined;

export function connectToGlobalRooms() {
	disconnectFromGlobalRooms();
	active = true;
	openGlobalRoomsSocket();
}

function reconnectToGlobalRooms() {
	if (!active || reconnectTimer !== undefined) return;
	globalRoomsStore.error = 'CONNECTION LOST — RECONNECTING...';
	reconnectTimer = setTimeout(() => {
		reconnectTimer = undefined;
		openGlobalRoomsSocket();
	}, reconnectDelay);
	reconnectDelay = Math.min(reconnectDelay * 2, 8000);
}

function openGlobalRoomsSocket() {
	let connection: WebSocket;

	try {
		const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
		connection = new WebSocket(`${protocol}://${window.location.host}/ws/global/rooms`);
	} catch {
		reconnectToGlobalRooms();
		return;
	}
	socket = connection;
	connectionTimer = setTimeout(() => {
		if (socket !== connection) return;
		socket = null;
		connection.close();
		connectionTimer = undefined;
		globalRoomsStore.connected = false;
		reconnectToGlobalRooms();
	}, 10000);

	connection.onopen = () => {
		if (socket !== connection) return;
		globalRoomsStore.connected = true;
	};

	connection.onmessage = (event) => {
		if (socket !== connection) return;
		try {
			const message = JSON.parse(event.data);
			if (message?.type !== 'ROOM_LIST') return;
			if (!Array.isArray(message.rooms)) throw new Error('Invalid room list');

			const validRooms = message.rooms.every(
				(room: GlobalRoom) =>
					room &&
					typeof room.roomId === 'string' &&
					typeof room.roomCode === 'string' &&
					typeof room.roomName === 'string' &&
					Number.isInteger(room.playerCount) &&
					Number.isInteger(room.playerTarget) &&
					typeof room.state === 'string' &&
					typeof room.full === 'boolean'
			);
			const roomIds = new Set(message.rooms.map((room: GlobalRoom) => room?.roomId));
			if (!validRooms || roomIds.size !== message.rooms.length) {
				throw new Error('Invalid room list');
			}

			clearTimeout(connectionTimer);
			connectionTimer = undefined;
			reconnectDelay = 1000;
			globalRoomsStore.rooms = message.rooms;
			globalRoomsStore.error = null;
		} catch {
			globalRoomsStore.error = 'Could not read room list update';
		}
	};

	connection.onclose = () => {
		if (socket !== connection) return;
		globalRoomsStore.connected = false;
		socket = null;
		clearTimeout(connectionTimer);
		connectionTimer = undefined;
		reconnectToGlobalRooms();
	};

	connection.onerror = () => {
		if (socket !== connection) return;
		globalRoomsStore.error = 'CONNECTION LOST — RECONNECTING...';
	};
}

export function disconnectFromGlobalRooms() {
	active = false;
	clearTimeout(reconnectTimer);
	clearTimeout(connectionTimer);
	reconnectTimer = undefined;
	connectionTimer = undefined;
	reconnectDelay = 1000;
	const connection = socket;
	socket = null;
	connection?.close();
	globalRoomsStore.connected = false;
	globalRoomsStore.rooms = [];
	globalRoomsStore.error = null;
}
