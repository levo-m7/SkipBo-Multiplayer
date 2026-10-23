const browser = typeof window !== 'undefined';

type GameSound = 'card' | 'turn' | 'wild' | 'chat';
export type AudioCategory = 'myCards' | 'opponentCards' | 'myTurn' | 'turnSwitched' | 'chat';
export type AudioPreferences = Record<AudioCategory, boolean>;
const defaults: AudioPreferences = { myCards: true, opponentCards: false, myTurn: true, turnSwitched: false, chat: true };
const storageKey = 'skipbo-audio-settings';
const soundNames: GameSound[] = ['card', 'turn', 'wild', 'chat'];
let sounds: Record<GameSound, HTMLAudioElement> | undefined;
let active = false;
let unlocked = false;
let unlocking = false;
let lifecycle = 0;
let preferences = { ...defaults };
let preferencesLoaded = false;

export function getGameAudioPreferences(): AudioPreferences {
    if (browser && !preferencesLoaded) {
        preferencesLoaded = true;
        try {
            const saved = JSON.parse(window.localStorage.getItem(storageKey) ?? 'null');
            if (saved && typeof saved === 'object') {
                for (const key of Object.keys(defaults) as AudioCategory[]) {
                    if (typeof saved[key] === 'boolean') preferences[key] = saved[key];
                }
            } else {
                if (window.localStorage.getItem('skipbo-game-sounds-muted') === 'true') preferences.myCards = false;
                if (window.localStorage.getItem('skipbo-notifications-muted') === 'true') {
                    preferences.myTurn = false;
                    preferences.chat = false;
                }
            }
        } catch {  }
    }
    return { ...preferences };
}

export function setGameAudioPreferences(value: AudioPreferences) {
    getGameAudioPreferences();
    preferences = { ...value };
    if (browser) {
        try { window.localStorage.setItem(storageKey, JSON.stringify(preferences)); } catch {  }
    }
    if (!preferences.myCards && !preferences.opponentCards) {
        sounds?.card.pause();
        sounds?.wild.pause();
    }
    if (!preferences.myTurn && !preferences.turnSwitched) sounds?.turn.pause();
    if (!preferences.chat) sounds?.chat.pause();
}

export function initializeGameAudio(): () => void {
    if (!browser) return () => {};
    getGameAudioPreferences();
    if (!sounds) {
        sounds = Object.fromEntries(soundNames.map((name) => {
            const sound = new Audio(`/sounds/${name}.mp3`);
            sound.preload = 'auto';
            return [name, sound];
        })) as Record<GameSound, HTMLAudioElement>;
    }
    active = true;
    function unlock(event: Event) {
        if (!event.isTrusted || unlocked || unlocking || !active) return;
        unlocking = true;
        const attempt = lifecycle;
        const attempts = soundNames.map((name) => {
            const sound = sounds![name];
            const volume = sound.volume;
            const muted = sound.muted;
            sound.muted = true;
            sound.volume = 0;
            function restore() {
                sound.pause();
                try { sound.currentTime = 0; } catch {  }
                sound.volume = volume;
                sound.muted = muted;
            }
            try {
                return Promise.resolve(sound.play()).then(() => true, () => false).finally(restore);
            } catch {
                restore();
                return Promise.resolve(false);
            }
        });
        void Promise.all(attempts).then((results) => {
            unlocking = false;
            unlocked = active && attempt === lifecycle && results.every(Boolean);
        });
    }
    function resetUnlock() {
        lifecycle++;
        unlocked = false;
    }
    const gestures = ['pointerdown', 'touchstart', 'click', 'keydown'];
    gestures.forEach((name) => window.addEventListener(name, unlock, { capture: true, passive: true }));
    window.addEventListener('pageshow', resetUnlock);
    window.addEventListener('visibilitychange', resetUnlock, true);
    return () => {
        active = false;
        resetUnlock();
        gestures.forEach((name) => window.removeEventListener(name, unlock, true));
        window.removeEventListener('pageshow', resetUnlock);
        window.removeEventListener('visibilitychange', resetUnlock, true);
        soundNames.forEach((name) => sounds?.[name].pause());
    };
}

export function playGameSound(name: GameSound, category: AudioCategory = name === 'turn' ? 'myTurn' : name === 'chat' ? 'chat' : 'myCards') {
    if (!browser || !active || !unlocked || !sounds || !preferences[category]) return;
    try {
        const sound = sounds[name];
        sound.volume = category === 'turnSwitched' ? 0.65 : 1;
        sound.currentTime = 0;
        void sound.play().catch(() => { unlocked = false; });
    } catch {
        unlocked = false;
    }
}
