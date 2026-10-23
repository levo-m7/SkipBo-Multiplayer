type Player = {
    id: string;
    username: string;
    avatar: number;
};

// Share concurrent requests so a fresh browser creates only one session.
let pendingPlayer: Promise<Player> | null = null;

export function getPlayer(): Promise<Player> {
    if (!pendingPlayer) {
        pendingPlayer = loadPlayer().finally(() => {
            pendingPlayer = null;
        });
    }
    return pendingPlayer;
}

async function loadPlayer(): Promise<Player> {
    const response = await fetch('/api/me');

    if (!response.ok) {
        throw new Error('Could not load player');
    }
    return response.json();
}

export async function updateUsername(username: string): Promise<Player>{
    const response = await fetch('/api/me/updateUsername', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ username })
    });

    if (!response.ok) {
        throw new Error('Could not update username');
    }

    return response.json();
}


export async function updateAvatar(avatar: number): Promise<Player> {
    const response = await fetch('/api/me/updateAvatar', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ avatar })
    });

    if (!response.ok) {
        throw new Error('Could not update avatar');
    }

    return response.json();
}