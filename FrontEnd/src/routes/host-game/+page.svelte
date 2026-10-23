<script lang="ts">
    import { goto } from '$app/navigation';
    import { createRoom } from '#lib/api/room.ts';

    let roomName = $state('');
    let selectedPlayers = $state(1);
    let hosting = $state(false);
    let hostError = $state<string | null>(null);

    function goBack() {
        goto('/');
    }

    function handleRoomNameInput(event: Event) {
        const input = event.currentTarget as HTMLInputElement;

        const cleanedRoomName = input.value
            .replace(/[^a-zA-Z0-9 _-]/g, '')
            .slice(0, 20);

        input.value = cleanedRoomName;
        roomName = cleanedRoomName;
    }

    function selectPlayers(amount: number) {
        selectedPlayers = amount;
    }

    async function hostGame() {
        if (hosting) return;
        const trimmedRoomName = roomName.trim();
        if (!/^[a-zA-Z0-9 _-]{1,20}$/.test(trimmedRoomName)) {
            hostError = 'ENTER A GAME NAME';
            return;
        }
        hosting = true;
        hostError = null;
        try {
            const room = await createRoom({
                roomName: trimmedRoomName,
                playerTarget: selectedPlayers
            });

            await goto(`/waiting-lobby?roomId=${encodeURIComponent(room.id)}`);
        }
        catch (error) {
            console.error('Could not create room:', error);
            hostError = 'COULD NOT HOST GAME';
        }
        finally {
            hosting = false;
        }
    }
</script>

<main class="game-page">

    <div class="page-header">

        <button
            class="back-button"
            type="button"
            onclick={goBack}
        >
            BACK
        </button>

        <h1 class="page-title host-title">
            HOST GAME
        </h1>

    </div>

    <section class="game-panel">

        <label for="room-name">
            GAME NAME
        </label>

        <input
            id="room-name"
            type="text"
            maxlength="20"
            placeholder="Enter game name..."
            value={roomName}
            oninput={handleRoomNameInput}
        >

        <h2>NUMBER OF PLAYERS</h2>

        <div class="player-grid">

            <button
                id={selectedPlayers === 1 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(1)}
            >
                ANY
            </button>

            <button
                id={selectedPlayers === 2 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(2)}
            >
                2 PLAYERS
            </button>

            <button
                id={selectedPlayers === 3 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(3)}
            >
                3 PLAYERS
            </button>

            <button
                id={selectedPlayers === 4 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(4)}
            >
                4 PLAYERS
            </button>

            <button
                id={selectedPlayers === 5 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(5)}
            >
                5 PLAYERS
            </button>

            <button
                id={selectedPlayers === 6 ? 'player-selected' : undefined}
                class="player-option"
                type="button"
                onclick={() => selectPlayers(6)}
            >
                6 PLAYERS
            </button>

        </div>

        {#if hostError}
            <p role="alert">{hostError}</p>
        {/if}

        <button
            class="main-action host-action"
            type="button"
            onclick={hostGame}
            disabled={hosting}
        >
            HOST GAME
        </button>

    </section>

</main>

<style>
    .game-page {
        width: 90%;
        max-width: 430px;
        min-height: 100svh;

        margin: 0 auto;
        padding: 22px 0;
    }

    .page-header {
        display: flex;
        align-items: center;

        gap: 12px;

        margin-bottom: 18px;
    }

    .page-title {
        width: fit-content;

        margin: 0;
        padding: 10px 20px;

        border: 3px solid var(--comic-ink);
        box-shadow: var(--comic-shadow);

        font-size: 28px;
        font-weight: 900;
        font-style: italic;
    }

    .host-title {
        background: var(--comic-paper-white);
    }

    .back-button {
        min-height: 42px;

        padding: 0 12px;

        background: var(--comic-paper-white);

        border: 3px solid var(--comic-ink);
        box-shadow: 3px 3px 0 var(--comic-ink);

        font-weight: 900;
        font-style: italic;
    }

    .game-panel {
        padding: 16px;

        background: var(--comic-cream);

        border: var(--comic-border);
        box-shadow: var(--comic-shadow);
    }

    .game-panel label,
    .game-panel h2 {
        display: block;

        margin-bottom: 8px;

        font-size: 16px;
        font-weight: 900;
        font-style: italic;
    }

    .game-panel input {
        width: 100%;
        height: 46px;

        margin-bottom: 16px;
        padding: 0 12px;

        background: var(--comic-paper-white);

        border: 2px solid var(--comic-ink);

        font-size: 18px;
    }

    .player-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);

        gap: 8px;

        margin-bottom: 18px;
    }

    .player-option {
        min-height: 52px;

        padding: 6px;

        background: var(--comic-paper-white);

        border: 3px solid var(--comic-ink);

        font-weight: 900;
    }

    #player-selected {
        background: var(--comic-lightpurple);
    }

    .main-action {
        width: 100%;
        min-height: 56px;

        padding: 0 16px;

        border: 3px solid var(--comic-ink);
        box-shadow: var(--comic-shadow);

        font-size: 22px;
        font-weight: 900;
        font-style: italic;
        text-align: left;
    }

    .host-action {
        background: var(--comic-lightpurple);
    }

    @media (hover: hover) and (pointer: fine) {

        .back-button,
        .player-option,
        .main-action {
            cursor: pointer;
        }

        .back-button,
        .player-option,
        .main-action {
            transition:
                transform 0.15s ease,
                filter 0.15s ease,
                box-shadow 0.15s ease;
        }

        .player-option:not(#player-selected):hover {
            transform:
                translateY(-2px)
                scale(1.03);

            filter: brightness(1.05);
        }

        .back-button:hover {
            transform: translateY(-2px);

            filter: brightness(1.05);
        }

        .host-action:hover {
            transform:
                translateY(-3px)
                scale(1.02);

            filter: brightness(1.05);

            box-shadow:
                7px
                7px
                0
                var(--comic-ink);
        }
    }

    @media (min-width: 600px) {

        .game-page {
            width: 82%;
            max-width: 620px;

            padding: 30px 0;
        }

        .page-header {
            gap: 16px;

            margin-bottom: 22px;
        }

        .page-title {
            padding: 12px 26px;

            font-size: 32px;
        }

        .back-button {
            min-height: 46px;

            padding: 0 14px;

            font-size: 15px;
        }

        .game-panel {
            padding: 20px;
        }

        .game-panel label,
        .game-panel h2 {
            font-size: 18px;
        }

        .game-panel input {
            height: 50px;

            font-size: 19px;
        }

        .player-grid {
            gap: 10px;
        }

        .player-option {
            min-height: 58px;

            font-size: 17px;
        }

        .main-action {
            min-height: 62px;

            font-size: 25px;
        }
    }

    @media (min-width: 1024px) {

        .game-page {
            width: 70%;
            max-width: 720px;

            padding: 40px 0;
        }

        .page-header {
            gap: 18px;
        }

        .page-title {
            padding: 14px 30px;

            font-size: 36px;
        }

        .back-button {
            min-height: 50px;

            padding: 0 16px;

            font-size: 16px;
        }

        .game-panel {
            padding: 24px;
        }

        .game-panel label,
        .game-panel h2 {
            font-size: 19px;
        }

        .game-panel input {
            height: 54px;

            font-size: 20px;
        }

        .player-option {
            min-height: 64px;

            font-size: 18px;
        }

        .main-action {
            min-height: 68px;

            font-size: 28px;
        }
    }

    @media (min-width: 1440px) {

        .game-page {
            max-width: 800px;

            padding: 48px 0;
        }

        .page-title {
            font-size: 40px;
        }

        .back-button {
            min-height: 54px;

            font-size: 17px;
        }

        .game-panel {
            padding: 28px;
        }

        .game-panel input {
            height: 58px;

            font-size: 21px;
        }

        .player-option {
            min-height: 68px;

            font-size: 19px;
        }

        .main-action {
            min-height: 72px;

            font-size: 30px;
        }
    }

    @media (min-width: 1920px) {

        .game-page {
            max-width: 900px;

            padding: 55px 0;
        }

        .page-title {
            padding: 16px 34px;

            font-size: 44px;
        }

        .back-button {
            min-height: 58px;

            padding: 0 18px;

            font-size: 18px;
        }

        .game-panel {
            padding: 32px;
        }

        .game-panel label,
        .game-panel h2 {
            font-size: 21px;
        }

        .game-panel input {
            height: 62px;

            font-size: 22px;
        }

        .player-grid {
            gap: 12px;
        }

        .player-option {
            min-height: 72px;

            font-size: 20px;
        }

        .main-action {
            min-height: 76px;

            font-size: 32px;
        }
    }

    @media (min-width: 2560px) {

        .game-page {
            max-width: 980px;
        }

        .page-title {
            font-size: 48px;
        }

        .game-panel {
            padding: 36px;
        }

        .main-action {
            min-height: 80px;

            font-size: 34px;
        }
    }
</style>