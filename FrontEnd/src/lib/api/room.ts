export type Room = {
    id: string;
    name: string;
    hostId: string;
    playerTarget: number;
    playerIds: string[];
    state: string;
};

type CreateRoomRequest = {
    roomName: string;
    playerTarget: number;
};

export async function createRoom(request: CreateRoomRequest): Promise<Room> {
    const response = await fetch('/api/rooms', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
    });

    if (!response.ok) {
        throw new Error(
            await response.text()
        );
    }

    return response.json();
}