type Player = {
    id: string | null;
    username: string;
    avatar: number
};

type Store = {
    player: Player;
};

export const store = $state<Store>({
    player: {
        id: null,
        username: 'username1',
        avatar: 1
    }
});