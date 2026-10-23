<script lang="ts">
    import { onMount, tick, untrack } from 'svelte';
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import GameCard from '#lib/components/game/GameCard.svelte';
    import PlayerGamePanel from '#lib/components/game/PlayerGamePanel.svelte';
    import { roomSocketStore, connectToRoom, releaseRoomConnection, sendChatMessage, sendGameAction, leaveGame as sendLeaveGame } from '#lib/state/roomSocket.svelte.ts';
    import { cardValue, type SelectedSource } from '#lib/types/game.ts';
    import { avatars } from '#lib/data/avatars.ts';
    import { initializeGameAudio, getGameAudioPreferences, setGameAudioPreferences, type AudioCategory } from '#lib/audio/gameAudio.ts';

    const roomId = $derived(page.url.searchParams.get('roomId'));
    const gameState = $derived(roomSocketStore.game?.roomId === roomId ? roomSocketStore.game : null);
    const opponents = $derived(gameState?.opponents ?? []);
    const localPlayer = $derived(gameState?.localPlayer);
    const playerCount = $derived(gameState?.playerCount ?? 0);
    const roomName = $derived(roomSocketStore.room?.roomName ?? '');
    const roomCode = $derived(roomSocketStore.room?.roomCode ?? roomId?.substring(0, 8) ?? '');
    const currentTurnPlayerId = $derived(gameState?.currentPlayerId ?? '');
    const yourTurn = $derived(gameState !== null && gameState.currentPlayerId === gameState.localPlayer.playerId);
    const gameplayEnabled = $derived(!!gameState && yourTurn && gameState.winnerPlayerId === null && roomSocketStore.connected && !roomSocketStore.reconnecting && !roomSocketStore.gamePending && !roomSocketStore.leavePending);
    const chatMessages = $derived(roomSocketStore.room?.chatMessages ?? []);
    const winnerPlayerId = $derived(gameState?.winnerPlayerId ?? null);
    const winner = $derived(winnerPlayerId === localPlayer?.playerId ? localPlayer : opponents.find((player) => player.playerId === winnerPlayerId));

    let mobileLayout = $state(false);
    let mobileChat = $state(false);
    const usesOpponentRail = $derived(playerCount >= 4 && mobileChat);
    const mobileGame = $derived(playerCount >= 2 && playerCount <= 6);
    let opponentRail = $state<HTMLElement>();
    let selectedSource = $state<SelectedSource | null>(null);
    let chatDialog = $state<HTMLDialogElement>();
    let winnerDialog = $state<HTMLDialogElement>();
    let leaveDialog = $state<HTMLDialogElement>();
    let lobbyNavigationPending = false;
    let chatMessagesElement: HTMLDivElement;
    let chatInput = $state('');
    let soundDialog = $state<HTMLDialogElement>();
    let audioPreferences = $state(getGameAudioPreferences());
    const allSoundsMuted = $derived(Object.values(audioPreferences).every((enabled) => !enabled));
    let chatInitialized = $state(false);
    let lastSeenMessageId = $state<string | undefined>();
    let chatHasUnread = $state(false);
    let viewportHeight = $state('100dvh');
    const emojis = ['😀', '😂', '❤️', '👍', '🎉', '😎'];
    const lastMessageId = $derived(chatMessages.at(-1)?.id);

    $effect(() => {
        const id = roomId;
        untrack(() => {
            if (!id) void goto('/');
            else void connectToRoom(id);
        });
        return releaseRoomConnection;
    });

    $effect(() => {
        if (roomSocketStore.unavailable) void goto('/');
        else if (!lobbyNavigationPending && roomSocketStore.room?.roomId === roomId && roomSocketStore.room.state === 'WAITING') {
            lobbyNavigationPending = true;
            void goto(`/waiting-lobby?roomId=${encodeURIComponent(roomId)}`);
        }
    });

    $effect(() => {
        roomSocketStore.gameRevision;
        selectedSource = null;
    });

    $effect(() => {
        const id = winnerPlayerId;
        if (id) void showWinner(id);
    });

    async function showWinner(id: string) {
        await tick();
        if (winnerPlayerId === id && winnerDialog && !winnerDialog.open) winnerDialog.showModal();
    }

    function toggleSound(category: AudioCategory) {
        audioPreferences = { ...audioPreferences, [category]: !audioPreferences[category] };
        setGameAudioPreferences(audioPreferences);
    }

    function muteAllSounds() {
        audioPreferences = { myCards: false, opponentCards: false, myTurn: false, turnSwitched: false, chat: false };
        setGameAudioPreferences(audioPreferences);
    }

    function leaveGame() {
        if (!roomSocketStore.leavePending && leaveDialog && !leaveDialog.open) leaveDialog.showModal();
    }

    function confirmLeaveGame() {
        if (sendLeaveGame()) leaveDialog?.close();
    }

    function selectCard(source: SelectedSource) {
        if (!gameplayEnabled) return;
        selectedSource = JSON.stringify(source) === JSON.stringify(selectedSource) ? null : source;
    }

    function playBuilding(buildingPileIndex: number) {
        if (!gameplayEnabled || !selectedSource) return;
        if (selectedSource.type === 'HAND') {
            sendGameAction({ type: 'PLAY_HAND_CARD', cardId: selectedSource.cardId, buildingPileIndex });
        } else if (selectedSource.type === 'STOCK') {
            sendGameAction({ type: 'PLAY_STOCK_CARD', buildingPileIndex });
        } else {
            sendGameAction({ type: 'PLAY_DISCARD_CARD', discardPileIndex: selectedSource.discardPileIndex, buildingPileIndex });
        }
    }

    function discardHand(discardPileIndex: number) {
        if (!gameplayEnabled || selectedSource?.type !== 'HAND') return;
        sendGameAction({ type: 'DISCARD_HAND_CARD', cardId: selectedSource.cardId, discardPileIndex });
    }

    $effect(() => {
        lastMessageId;
        void scrollChatToBottom();
    });

    $effect(() => {
        const latestId = lastMessageId;
        if (!roomSocketStore.room) {
            chatInitialized = false;
            lastSeenMessageId = undefined;
            chatHasUnread = false;
            return;
        }
        if (!chatInitialized) {
            lastSeenMessageId = latestId;
            chatInitialized = true;
            return;
        }
        if (!mobileChat) chatHasUnread = false;
        if (latestId === lastSeenMessageId) return;
        lastSeenMessageId = latestId;
        chatHasUnread = mobileChat && !chatDialog?.open;
    });

    $effect(() => {
        const playerId = currentTurnPlayerId;
        untrack(() => { void followOpponentTurn(playerId); });
    });

    async function followOpponentTurn(playerId: string) {
        await tick();
        if (!usesOpponentRail || currentTurnPlayerId !== playerId) return;
        const rail = opponentRail;
        if (!rail) return;
        if (playerId === localPlayer?.playerId) {
            rail.scrollTo({ left: 0, behavior: 'smooth' });
            return;
        }
        const card = Array.from(rail.querySelectorAll<HTMLElement>('[data-player-id]'))
            .find((panel) => panel.dataset.playerId === playerId);
        if (!card) return;
        const bounds = rail.getBoundingClientRect();
        const target = card.getBoundingClientRect();
        const left = rail.scrollLeft + target.left - bounds.left - rail.clientLeft - (rail.clientWidth - target.width) / 2;
        rail.scrollTo({ left: Math.max(0, Math.min(left, rail.scrollWidth - rail.clientWidth)), behavior: 'smooth' });
    }

    function markChatRead() {
        chatHasUnread = false;
        lastSeenMessageId = lastMessageId;
    }

    function openChat() {
        if (!chatDialog) return;
        chatDialog.showModal();
        markChatRead();
        void scrollChatToBottom();
    }

    async function scrollChatToBottom() {
        await tick();
        if (chatMessagesElement) chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
    }

    function submitChat(event: SubmitEvent) {
        event.preventDefault();
        if (sendChatMessage(chatInput)) chatInput = '';
    }

    onMount(() => {
        const stopAudio = initializeGameAudio();
        audioPreferences = getGameAudioPreferences();
        const desktopLayout = window.matchMedia('(min-width: 1024px)');
        function updateChatLayout() {
            mobileChat = !desktopLayout.matches;
            if (!mobileChat && chatDialog?.open) chatDialog.close();
            markChatRead();
        }
        const viewport = window.visualViewport;
        function updateViewport() {
            mobileLayout = window.matchMedia('(max-width: 599px)').matches;
            viewportHeight = `${viewport?.height ?? window.innerHeight}px`;
        }
        updateChatLayout();
        updateViewport();
        desktopLayout.addEventListener('change', updateChatLayout);
        viewport?.addEventListener('resize', updateViewport);
        window.addEventListener('resize', updateViewport);
        return () => {
            stopAudio();
            desktopLayout.removeEventListener('change', updateChatLayout);
            viewport?.removeEventListener('resize', updateViewport);
            window.removeEventListener('resize', updateViewport);
        };
    });
</script>


<svelte:head>
    <title>{`SkipBo — ${roomName}`}</title>
</svelte:head>

{#snippet chatContent()}
    <div id="chat-container">
        <header id="chat-header">
            <h2>ROOM CHAT<small hidden></small></h2>
            <button id="close-chat-button" type="button" aria-label="Close chat" onclick={() => chatDialog?.close()}>✕</button>
        </header>
        <div id="chat-messages" bind:this={chatMessagesElement}>
            {#each chatMessages as message (message.id)}
                <div class="chat-message">
                    <img src={avatars[message.avatar - 1] ?? avatars[0]} alt={`${message.username}'s avatar`}>
                    <div>
                        <strong>{message.username}<span>#{message.playerId.substring(0, 3)}</span></strong>
                        <p>{message.message}</p>
                    </div>
                </div>
            {/each}
        </div>
        <form id="chat-form" onsubmit={submitChat}>
            <input id="chat-input" type="text" aria-label="Chat message" placeholder="Type a message..." bind:value={chatInput} maxlength="200" enterkeyhint="send" autocomplete="off">
            <button id="send-chat-button" type="submit" disabled={!chatInput.trim()}>SEND</button>
            <div class="chat-emojis" aria-label="Add an emoji">
                {#each emojis as emoji}
                    <button type="button" aria-label={`Add ${emoji}`} disabled={chatInput.length + emoji.length > 200} onclick={() => chatInput += emoji}>{emoji}</button>
                {/each}
            </div>
        </form>
    </div>
{/snippet}

{#if gameState && localPlayer}
<main id="game-page" class={`players-${playerCount}`} class:mobile-multiplayer={(mobileLayout && playerCount > 2) || (playerCount >= 4 && mobileChat)} style:--game-viewport-height={mobileGame ? viewportHeight : undefined}>
    <div id="game-board">
        <header id="game-header">
            {#if mobileGame}
                <button id="leave-button" type="button" onclick={leaveGame}>LEAVE</button>
            {:else}
                <a id="game-logo" href="/" aria-label="SkipBo home">SKIPBO</a>
            {/if}
            <div id="game-room">
                {#if mobileGame}
                    <span title={roomName}>ROOM: <strong>{roomName}</strong></span>
                    <small>CODE: {roomCode}</small>
                {:else}
                    <span title={roomName}>ROOM: <strong>{roomName}</strong></span>
                    <small>CODE: {roomCode}</small>
                {/if}
            </div>
            <div class="audio-controls" role="group" aria-label="Sound settings">
                <button type="button" onclick={() => soundDialog?.showModal()} aria-label="Sound settings" title="Sound settings">{allSoundsMuted ? '🔇' : '🔊'}</button>
            </div>
            <button id="chat-button" class:has-unread-chat={chatHasUnread} type="button" aria-label={chatHasUnread ? 'Open chat, new message' : 'Open chat'} onclick={openChat}>💬</button>
        </header>

        <section class="opponents" aria-label="Other players" bind:this={opponentRail}>
            {#each opponents as player (player.playerId)}
                <PlayerGamePanel {player} threePlayer={playerCount === 3} compactOpponents={(mobileLayout && playerCount > 2) || playerCount === 4 || playerCount === 5 || playerCount === 6 ? playerCount : 0} twoPlayer={mobileGame} active={currentTurnPlayerId === player.playerId} />
            {/each}
        </section>

        <section id="shared-table" aria-label="Shared table">
            <h1>BUILDING PILES</h1>
            <div class="table-piles">
                <div class="side-pile">
                    <h2>DRAW</h2>
                    <div class="table-stack">
                        <div class="stack-back" aria-hidden="true"><GameCard back /></div>
                        <GameCard back label={`Draw pile, ${gameState.drawPileCount} cards`} />
                    </div>
                    <p>{gameState.drawPileCount} CARDS</p>
                </div>
                <div class="building-piles">
                    {#each gameState.buildingPiles as pile, index (index)}
                        {@const value = cardValue(pile.topCard)}
                        <div class="building-pile">
                            <GameCard {value} wildValue={pile.topCard?.type === 'WILD' ? pile.size : undefined} clickable disabled={!gameplayEnabled || !selectedSource} label={`Building pile ${index + 1}, ${value === 'WILD' ? `WILD representing ${pile.size}` : value ?? 'empty'}, ${pile.size} cards`} onclick={() => playBuilding(index)} />
                            <span>{index + 1}</span>
                        </div>
                    {/each}
                </div>
            </div>
        </section>

        {#if !mobileGame}
        <p id="turn-indicator" class:your-turn={yourTurn} role="status">
            {yourTurn ? '★ YOUR TURN! ★' : `${opponents.find((player) => player.playerId === currentTurnPlayerId)?.username.toUpperCase() ?? ''}'S TURN`}
        </p>
        {/if}

        <PlayerGamePanel player={localPlayer} threePlayer={playerCount === 3} twoPlayer={mobileGame} local active={yourTurn} {gameplayEnabled} {selectedSource} onselect={selectCard} ondiscard={discardHand} />
        {#if roomSocketStore.error}<p role="alert">{roomSocketStore.error}</p>{/if}
    </div>

    {#if mobileChat}
        <dialog id="chat-dialog" aria-label="Room chat" bind:this={chatDialog} onclose={markChatRead} style:--chat-viewport-height={viewportHeight}>
            {@render chatContent()}
        </dialog>
    {:else}
        <aside id="desktop-chat" aria-label="Room chat">
            {@render chatContent()}
        </aside>
    {/if}
</main>

    <dialog id="sound-dialog" bind:this={soundDialog} aria-labelledby="sound-title">
        <div class="sound-heading"><h2 id="sound-title">SOUND</h2><button type="button" aria-label="Close sound settings" onclick={() => soundDialog?.close()}>✕</button></div>
        {#each [
            { title: 'MY CARDS', settings: [{ key: 'myCards', label: 'My card sounds' }] },
            { title: 'TABLE', settings: [{ key: 'opponentCards', label: 'Opponent card sounds' }] },
            { title: 'NOTIFICATIONS', settings: [{ key: 'myTurn', label: 'My turn' }, { key: 'turnSwitched', label: 'Turn switched' }, { key: 'chat', label: 'Chat message' }] }
        ] as group}
            <h3>{group.title}</h3>
            {#each group.settings as setting}
                <div class="sound-setting"><span>{setting.label}</span><button type="button" aria-label={setting.label} aria-pressed={audioPreferences[setting.key as AudioCategory]} onclick={() => toggleSound(setting.key as AudioCategory)}>{audioPreferences[setting.key as AudioCategory] ? 'ON' : 'OFF'}</button></div>
            {/each}
        {/each}
        <button class="mute-all" type="button" onclick={muteAllSounds}>MUTE ALL</button>
    </dialog>

    <dialog id="leave-game-dialog" bind:this={leaveDialog} aria-labelledby="leave-game-title">
        <h2 id="leave-game-title">LEAVE GAME?</h2>
        <p>This will return everyone to the lobby.</p>
        <div class="leave-game-actions">
            <button type="button" onclick={() => leaveDialog?.close()}>STAY</button>
            <button type="button" onclick={confirmLeaveGame} disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending}>LEAVE</button>
        </div>
    </dialog>

    <dialog id="winner-dialog" bind:this={winnerDialog} aria-label="Game result">
        <h2>{winnerPlayerId === localPlayer.playerId ? 'YOU WON!' : `PLAYER ${winner?.playerNumber ?? ''} — ${winner?.username ?? ''} WON!`}</h2>
        <button type="button" onclick={() => winnerDialog?.close()}>CLOSE</button>
    </dialog>
{:else}
    <main id="game-loading"><p role="status">LOADING GAME...</p></main>
{/if}

{#if roomSocketStore.notice}
    <div class="game-toast" role="status" aria-live="polite">
        <strong>{roomSocketStore.notice.title}</strong>
        {#if roomSocketStore.notice.reason}<span>{roomSocketStore.notice.reason}</span>{/if}
    </div>
{/if}

<style>
    #game-loading,
    #winner-dialog,
    #leave-game-dialog {
        margin: auto;
        padding: 20px;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        box-shadow: 4px 4px 0 var(--comic-ink);
        font-weight: 900;
    }

    #winner-dialog::backdrop,
    #leave-game-dialog::backdrop {
        background: rgba(16, 25, 31, 0.6);
    }

    .game-toast {
        position: fixed;
        top: calc(env(safe-area-inset-top) + 10px);
        left: 50%;
        transform: translateX(-50%);
        width: min(360px, calc(100vw - 24px));
        z-index: 10000;
        padding: 10px 14px;
        background: var(--comic-red);
        color: var(--comic-ink);
        border: 3px solid var(--comic-ink);
        box-shadow: 4px 4px 0 var(--comic-ink);
        font-weight: 900;
        font-style: italic;
        text-align: center;
        pointer-events: none;
    }

    .game-toast strong,
    .game-toast span {
        display: block;
    }

    #leave-game-dialog {
        width: min(360px, calc(100vw - 24px));
    }

    #leave-game-dialog p {
        margin-top: 8px;
    }

    .leave-game-actions {
        display: flex;
        justify-content: flex-end;
        gap: 10px;
        margin-top: 16px;
    }

    .leave-game-actions button {
        min-height: 40px;
        padding: 6px 12px;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        box-shadow: 2px 2px 0 var(--comic-ink);
        font-weight: 900;
    }

    :global(body) {
        background: radial-gradient(circle, rgba(16, 25, 31, 0.18) 1px, transparent 1.5px), var(--comic-blue);
        background-size: 7px 7px;
    }

    #game-page {
        width: 100%;
        max-width: 430px;
        min-height: 100svh;
        margin: 0 auto;
        padding: max(8px, env(safe-area-inset-top)) max(10px, env(safe-area-inset-right)) max(12px, env(safe-area-inset-bottom)) max(10px, env(safe-area-inset-left));
    }

    #game-board {
        display: flex;
        flex-direction: column;
        gap: 12px;
        min-width: 0;
    }

    #game-header {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) 44px;
        align-items: center;
        gap: 10px;
        min-height: 44px;
    }

    #game-logo {
        color: var(--comic-orange);
        font-size: 29px;
        font-weight: 900;
        font-style: italic;
        letter-spacing: -2px;
        text-decoration: none;
        -webkit-text-stroke: 1px var(--comic-ink);
        paint-order: stroke fill;
        text-shadow: 2px 2px 0 var(--comic-ink);
        transform: rotate(-3deg);
    }

    #game-room {
        min-width: 0;
        text-align: center;
    }

    #game-room span {
        font-size: 10px;
        font-weight: 900;
    }

    #game-room strong {
        font-size: 13px;
    }

    #game-room small {
        display: block;
        margin-top: 2px;
        font-size: 8px;
        font-weight: 700;
    }

    #chat-button {
        height: 44px;
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 3px 3px 0 var(--comic-ink);
        font-size: 20px;
    }

    #chat-button.has-unread-chat {
        background: var(--comic-yellow);
    }

    .opponents {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 8px;
        min-width: 0;
        padding: 3px 0;
    }

    :is(.players-2, .mobile-multiplayer) .opponents {
        grid-template-columns: minmax(0, 1fr);
        --opponent-stock-width: 44px;
    }

    .players-3 .opponents,
    .players-5 .opponents {
        grid-template-columns: repeat(2, minmax(0, 1fr));
        --opponent-stock-width: 32px;
    }

    .players-6 .opponents {
        --opponent-stock-width: 24px;
    }

    #shared-table {
        padding: 10px;
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        box-shadow: 4px 4px 0 var(--comic-ink);
    }

    #shared-table h1 {
        margin-bottom: 8px;
        font-size: 13px;
        font-weight: 900;
        font-style: italic;
        text-align: center;
    }

    .table-piles {
        display: grid;
        --table-draw-ratio: calc(0.7 / 5.4);
        --table-building-ratio: calc(4 / 5.4);
        --table-gap: 8px;
        grid-template-columns: minmax(0, calc((100% - 2 * var(--table-gap)) * var(--table-draw-ratio))) minmax(0, calc((100% - 2 * var(--table-gap)) * var(--table-building-ratio)));
        align-items: center;
        row-gap: 8px;
        column-gap: calc(1.5 * var(--table-gap) + (100% - 2 * var(--table-gap)) * var(--table-draw-ratio) / 2);
    }

    .side-pile {
        min-width: 0;
        text-align: center;
    }

    .side-pile h2 {
        margin-bottom: 5px;
        font-size: 8px;
        font-weight: 900;
    }

    .side-pile p {
        margin-top: 5px;
        font-size: 7px;
        font-weight: 900;
    }

    .table-stack {
        position: relative;
        margin-left: 3px;
    }

    .stack-back {
        position: absolute;
        inset: 0;
        transform: translate(-3px, -2px) rotate(-4deg);
    }

    .building-piles {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 5px;
        min-width: 0;
        padding-top: 5px;
    }

    .building-pile {
        min-width: 0;
        text-align: center;
    }

    .building-pile span {
        display: block;
        margin-top: 5px;
        font-size: 9px;
        font-weight: 900;
    }

    #turn-indicator {
        padding: 7px 10px;
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        box-shadow: 3px 3px 0 var(--comic-ink);
        font-size: 16px;
        font-weight: 900;
        font-style: italic;
        text-align: center;
        transform: rotate(-0.5deg);
    }

    #turn-indicator.your-turn {
        background: #B8FF3A;
    }

    button,
    a {
        touch-action: manipulation;
    }

    button {
        cursor: pointer;
    }

    button:focus-visible,
    a:focus-visible,
    input:focus-visible {
        outline: 3px solid var(--comic-purple);
        outline-offset: 2px;
    }

    #desktop-chat {
        display: none;
    }

    #chat-dialog,
    #desktop-chat {
        width: calc(100% - 20px);
        max-width: 400px;
        max-height: calc(var(--chat-viewport-height, 100dvh) - 30px);
        margin: auto;
        padding: 0;
        overflow: hidden;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        border-radius: 8px;
        box-shadow: 6px 6px 0 var(--comic-ink);
    }

    #chat-dialog::backdrop {
        background: rgba(16, 25, 31, 0.7);
    }

    #chat-container {
        height: min(520px, calc(var(--chat-viewport-height, 100dvh) - 30px));
        display: grid;
        grid-template-rows: auto minmax(0, 1fr) auto;
    }

    #chat-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 9px 10px;
        background: var(--comic-yellow);
        border-bottom: 3px solid var(--comic-ink);
    }

    #chat-header h2 {
        font-size: 18px;
        font-weight: 900;
        font-style: italic;
    }

    #chat-header small {
        margin-left: 5px;
        font-size: 9px;
        font-style: normal;
    }

    #close-chat-button {
        width: 36px;
        height: 36px;
        background: var(--comic-red);
        border: 2px solid var(--comic-ink);
        font-size: 16px;
        font-weight: 900;
    }

    #chat-messages {
        min-height: 0;
        overflow-y: auto;
        overscroll-behavior: contain;
        padding: 10px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .chat-message {
        flex-shrink: 0;
        overflow-wrap: anywhere;
        display: grid;
        grid-template-columns: 34px minmax(0, 1fr);
        gap: 7px;
        align-items: start;
    }

    .chat-message img {
        width: 34px;
        height: 34px;
        object-fit: cover;
        border: 2px solid var(--comic-ink);
        border-radius: 4px;
    }

    .chat-message strong {
        font-size: 13px;
    }

    .chat-message strong span {
        color: var(--comic-ink-soft);
        font-size: 11px;
        font-weight: 600;
    }

    .chat-message p {
        margin-top: 2px;
        font-size: 13px;
    }

    #chat-form {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 5px;
        padding: 9px;
        border-top: 3px solid var(--comic-ink);
    }

    #chat-input {
        min-width: 0;
        height: 40px;
        padding: 0 9px;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        font-size: 16px;
    }

    #send-chat-button {
        min-height: 40px;
        padding: 0 10px;
        background: var(--comic-blue);
        border: 2px solid var(--comic-ink);
        font-size: 12px;
        font-weight: 900;
    }

    .chat-emojis {
        grid-column: 1 / -1;
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
    }

    .chat-emojis button {
        min-width: 36px;
        min-height: 36px;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        font-size: 20px;
    }

    @media (min-width: 600px) {
        #game-page {
            max-width: 860px;
            padding: 16px;
        }

        #game-board {
            gap: 16px;
        }

        #game-logo {
            font-size: 36px;
        }

        .opponents {
            gap: 12px;
            --opponent-stock-width: 36px;
        }

        #shared-table {
            padding: 16px;
        }

        .table-piles {
            max-width: 510px;
            margin: 0 auto;
            --table-gap: 14px;
            row-gap: 14px;
        }

        .side-pile h2,
        .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1024px) {
        #game-page {
            display: grid;
            grid-template-columns: minmax(0, 1fr) 280px;
            gap: 20px;
            max-width: 1440px;
            padding: 20px;
        }

        #game-header {
            grid-template-columns: auto minmax(0, 1fr);
        }

        #chat-button {
            display: none;
        }

        #game-room {
            text-align: right;
        }

        .players-5 .opponents {
            grid-template-columns: repeat(4, minmax(0, 1fr));
        }

        #shared-table h1 {
            font-size: 17px;
        }

        #turn-indicator {
            font-size: 20px;
        }

        #desktop-chat {
            position: sticky;
            top: 20px;
            display: block;
            width: 100%;
            max-width: none;
            max-height: none;
            min-height: 0;
            height: calc(100svh - 40px);
            margin: 0;
            align-self: start;
        }

        #chat-container {
            width: 100%;
            height: 100%;
            min-height: 0;
        }

        #close-chat-button {
            display: none;
        }

        #chat-header {
            min-height: 50px;
        }
    }

    @media (min-width: 1440px) {
        #game-page {
            grid-template-columns: minmax(0, 1fr) 320px;
            gap: 26px;
            padding: 26px;
        }

        .players-6 .opponents {
            grid-template-columns: repeat(5, minmax(0, 1fr));
            --opponent-stock-width: 38px;
        }

        #chat-messages {
            padding: 14px;
            gap: 14px;
        }
    }

    @media (min-width: 1920px) {
        #game-page {
            max-width: 1800px;
            grid-template-columns: minmax(0, 1fr) 360px;
            padding: 32px;
        }

        #game-board {
            gap: 20px;
        }

        #game-logo {
            font-size: 42px;
        }

        .opponents {
            --opponent-stock-width: 46px;
        }
    }
    :is(.players-2, .mobile-multiplayer) {
        min-height: 100svh;
        min-height: 100dvh;
        padding-bottom: calc(16px + env(safe-area-inset-bottom));
    }

    :is(.players-2, .mobile-multiplayer) #game-header {
        grid-template-columns: auto minmax(0, 1fr) 44px;
    }

    #leave-button {
        min-height: 44px;
        padding: 0 9px;
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 3px 3px 0 var(--comic-ink);
        font-size: 12px;
        font-weight: 900;
    }

    :is(.players-2, .mobile-multiplayer) #game-room {
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-height: 50px;
        padding: 4px 7px;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 3px 3px 0 var(--comic-ink);
        text-align: left;
        overflow-wrap: anywhere;
    }

    :is(.players-2, .mobile-multiplayer) #game-room span,
    :is(.players-2, .mobile-multiplayer) #game-room strong {
        font-size: 14px;
    }

    :is(.players-2, .mobile-multiplayer) #game-room small {
        font-size: 12px;
    }

    :is(.players-2, .mobile-multiplayer) .table-piles {
        --table-draw-ratio: calc(0.8 / 5.6);
        --table-building-ratio: calc(4 / 5.6);
        --table-gap: 7px;
        row-gap: 7px;
    }

    :is(.players-2, .mobile-multiplayer) .side-pile h2,
    :is(.players-2, .mobile-multiplayer) .side-pile p {
        font-size: 9px;
    }

    @media (max-width: 599px) {
        #game-page.mobile-multiplayer .opponents {
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 7px;
            padding: 4px;
        }

        .players-4.mobile-multiplayer .opponents {
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 5px;
        }

        :is(.players-5, .players-6).mobile-multiplayer .opponents {
            display: flex;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scroll-snap-type: x proximity;
            scroll-padding: 4px;
        }

        :is(.players-5, .players-6) .opponents > :global(.compact-opponent) {
            flex: 0 0 43%;
            scroll-snap-align: start;
        }

        :is(.players-2, .mobile-multiplayer) {
            min-height: 100svh;
            min-height: var(--game-viewport-height, 100dvh);
            padding-top: max(6px, env(safe-area-inset-top));
            padding-bottom: max(10px, env(safe-area-inset-bottom));
        }

        :is(.players-2, .mobile-multiplayer) #game-board {
            gap: 7px;
        }

        :is(.players-2, .mobile-multiplayer) #game-header {
            gap: 6px;
        }

        :is(.players-2, .mobile-multiplayer) #game-room small {
            margin-top: 1px;
        }

        :is(.players-2, .mobile-multiplayer) .opponents {
            padding: 0;
        }

        :is(.players-2, .mobile-multiplayer) #shared-table {
            min-height: 140px;
            padding: 8px;
        }

        :is(.players-2, .mobile-multiplayer) #shared-table h1 {
            margin-bottom: 4px;
        }

        :is(.players-2, .mobile-multiplayer) .table-piles {
            grid-template-rows: auto auto auto;
            align-items: start;
            width: 100%;
            max-width: 310px;
            margin: 0 auto;
            --table-gap: 6px;
            row-gap: 0;
        }

        :is(.players-2, .mobile-multiplayer) .side-pile,
        :is(.players-2, .mobile-multiplayer) .building-piles,
        :is(.players-2, .mobile-multiplayer) .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        :is(.players-2, .mobile-multiplayer) .side-pile {
            grid-column: 1;
        }


        :is(.players-2, .mobile-multiplayer) .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        :is(.players-2, .mobile-multiplayer) .table-stack,
        :is(.players-2, .mobile-multiplayer) .side-pile > :global(.game-card),
        :is(.players-2, .mobile-multiplayer) .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        :is(.players-2, .mobile-multiplayer) .side-pile p,
        :is(.players-2, .mobile-multiplayer) .building-pile span {
            grid-row: 3;
        }

        :is(.players-2, .mobile-multiplayer) .building-pile span {
            margin-top: 3px;
        }

        :is(.players-2, .mobile-multiplayer) .side-pile h2 {
            margin-bottom: 3px;
        }

        :is(.players-2, .mobile-multiplayer) .side-pile p {
            margin-top: 3px;
        }
    }
    @media (min-width: 1024px) {
        #game-page.players-2 {
            width: 95%;
            max-width: 1520px;
            grid-template-columns: minmax(0, 1fr) minmax(230px, 23%);
            align-content: start;
            align-items: stretch;
            padding: 16px;
        }

        .players-2 #game-board {
            gap: 12px;
        }

        .players-2 #desktop-chat {
            position: static;
            height: auto;
            align-self: stretch;
            contain: size;
            overflow: hidden;
        }

        .players-2 #chat-container {
            height: 100%;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .players-2 #shared-table {
            padding: 14px;
        }

        .players-2 #shared-table h1 {
            font-size: 18px;
        }

        .players-2 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-2 .side-pile,
        .players-2 .building-piles,
        .players-2 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-2 .side-pile {
            grid-column: 1;
        }


        .players-2 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-2 .table-stack,
        .players-2 .side-pile > :global(.game-card),
        .players-2 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-2 .side-pile p,
        .players-2 .building-pile span {
            grid-row: 3;
        }

        .players-2 .side-pile h2,
        .players-2 .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-2 {
            max-width: 1640px;
            padding: 26px;
        }

        .players-2 #game-board {
            gap: 18px;
        }

        .players-2 #shared-table {
            padding: 18px;
        }

        .players-2 #shared-table h1 {
            font-size: 19px;
        }

        .players-2 .table-piles {
            max-width: 580px;
        }

        .players-2 #game-room span,
        .players-2 #game-room strong {
            font-size: 16px;
        }

        .players-2 #game-room small {
            font-size: 13px;
        }
    }

    @media (min-width: 1920px) {
        #game-page.players-2 {
            max-width: 1800px;
            padding: 32px;
        }
    }
    .players-3 {
        min-height: var(--game-viewport-height, 100dvh);
    }

    .players-3 #game-header {
        grid-template-columns: auto minmax(0, 1fr) 44px;
    }

    .players-3 #game-room {
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-height: 50px;
        padding: 4px 7px;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 3px 3px 0 var(--comic-ink);
        text-align: left;
        overflow-wrap: anywhere;
    }

    .players-3 #game-room span,
    .players-3 #game-room strong {
        font-size: 14px;
    }

    .players-3 #game-room small {
        font-size: 12px;
    }

    .players-3 .opponents {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .players-3 .table-piles {
        --table-draw-ratio: calc(0.8 / 5.6);
        --table-building-ratio: calc(4 / 5.6);
    }

    @media (min-width: 600px) {
        .players-3 #game-board {
            gap: 12px;
        }

        .players-3 .opponents {
            padding: 4px;
            gap: 10px;
        }
    }

    @media (min-width: 600px) {
        .players-3 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-3 .side-pile,
        .players-3 .building-piles,
        .players-3 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-3 .side-pile {
            grid-column: 1;
        }


        .players-3 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-3 .table-stack,
        .players-3 .side-pile > :global(.game-card),
        .players-3 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-3 .side-pile p,
        .players-3 .building-pile span {
            grid-row: 3;
        }

        .players-3 .side-pile h2,
        .players-3 .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1024px) {
        #game-page.players-3 {
            width: 95%;
            max-width: 1520px;
            grid-template-columns: minmax(0, 1fr) minmax(230px, 23%);
            align-content: start;
            align-items: stretch;
            padding: 16px;
        }

        .players-3 #game-board {
            gap: 12px;
        }

        .players-3 #desktop-chat {
            position: static;
            height: auto;
            align-self: stretch;
            contain: size;
            overflow: hidden;
        }

        .players-3 #chat-container {
            height: 100%;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .players-3 #shared-table {
            padding: 14px;
        }

        .players-3 #shared-table h1 {
            font-size: 18px;
        }


    }

    @media (min-width: 1440px) {
        #game-page.players-3 {
            max-width: 1640px;
            padding: 26px;
        }

        .players-3 #game-board {
            gap: 18px;
        }

        .players-3 #shared-table {
            padding: 18px;
        }

        .players-3 #shared-table h1 {
            font-size: 19px;
        }

        .players-3 .table-piles {
            max-width: 580px;
        }

        .players-3 #game-room span,
        .players-3 #game-room strong {
            font-size: 16px;
        }

        .players-3 #game-room small {
            font-size: 13px;
        }
    }

    @media (min-width: 1920px) {
        #game-page.players-3 {
            max-width: 1800px;
            padding: 32px;
        }
    }
    @media (min-width: 1024px) {
        .players-3 #game-header {
            grid-template-columns: auto minmax(0, 1fr);
        }

        .players-3 .opponents {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 10px;
        }
    }

    @media (min-width: 1440px) {
        .players-3 .opponents {
            gap: 14px;
        }
    }
    @media (max-width: 1023px) {
        #game-page.players-4 .opponents {
            display: flex;
            gap: 7px;
            padding: 4px;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scroll-snap-type: x proximity;
            scroll-padding: 4px;
        }

        .players-4 .opponents > :global(.three-opponents) {
            flex: 0 0 160px;
            scroll-snap-align: start;
        }
    }

    @media (min-width: 600px) and (max-width: 1023px) {
        .players-4 #game-board {
            gap: 12px;
        }

        .players-4 :global(.local .discard-cards) {
            max-width: 260px;
        }

        .players-4 :global(.local .hand) {
            max-width: 490px;
        }
        .players-4 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-4 .side-pile,
        .players-4 .building-piles,
        .players-4 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-4 .side-pile {
            grid-column: 1;
        }


        .players-4 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-4 .table-stack,
        .players-4 .side-pile > :global(.game-card),
        .players-4 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-4 .side-pile p,
        .players-4 .building-pile span {
            grid-row: 3;
        }

        .players-4 .side-pile h2,
        .players-4 .side-pile p {
            font-size: 10px;
        }
    }
    @media (min-width: 1024px) {
        #game-page.players-4 {
            width: 95%;
            max-width: 1520px;
            grid-template-columns: minmax(0, 1fr) minmax(230px, 23%);
            align-content: start;
            align-items: stretch;
            padding: 16px;
        }

        .players-4 #game-board {
            gap: 12px;
        }

        .players-4 #desktop-chat {
            position: static;
            height: auto;
            align-self: stretch;
            contain: size;
            overflow: hidden;
        }

        .players-4 #chat-container {
            height: 100%;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .players-4 #shared-table {
            padding: 14px;
        }

        .players-4 #shared-table h1 {
            font-size: 18px;
        }

        .players-4 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-4 .side-pile,
        .players-4 .building-piles,
        .players-4 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-4 .side-pile {
            grid-column: 1;
        }


        .players-4 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-4 .table-stack,
        .players-4 .side-pile > :global(.game-card),
        .players-4 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-4 .side-pile p,
        .players-4 .building-pile span {
            grid-row: 3;
        }

        .players-4 .side-pile h2,
        .players-4 .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-4 {
            max-width: 1640px;
            padding: 26px;
        }

        .players-4 #game-board {
            gap: 18px;
        }

        .players-4 #shared-table {
            padding: 18px;
        }

        .players-4 #shared-table h1 {
            font-size: 19px;
        }

        .players-4 .table-piles {
            max-width: 580px;
        }

        .players-4 #game-room span,
        .players-4 #game-room strong {
            font-size: 16px;
        }

        .players-4 #game-room small {
            font-size: 13px;
        }
    }

    @media (min-width: 1920px) {
        #game-page.players-4 {
            max-width: 1800px;
            padding: 32px;
        }
    }
    @media (min-width: 1024px) {
    .players-4 #game-room {
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-height: 50px;
        padding: 4px 7px;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 3px 3px 0 var(--comic-ink);
        text-align: left;
        overflow-wrap: anywhere;
    }

    .players-4 #game-room span,
    .players-4 #game-room strong {
        font-size: 14px;
    }

    .players-4 #game-room small {
        font-size: 12px;
    }


        .players-4 #game-header {
            grid-template-columns: auto minmax(0, 1fr);
        }

        #game-page.players-4 .opponents {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            align-items: stretch;
            gap: 8px;
            padding: 4px;
            overflow: visible;
            scroll-snap-type: none;
        }

        .players-4 .opponents > :global(.three-opponents) {
            min-width: 0;
            scroll-snap-align: none;
        }
    }

    @media (min-width: 1200px) {
        #game-page.players-4 .opponents {
            gap: 10px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-4 .opponents {
            gap: 12px;
        }

        .players-4 #game-room span,
        .players-4 #game-room strong {
            font-size: 16px;
        }

        .players-4 #game-room small {
            font-size: 13px;
        }
    }
    @media (max-width: 1023px) {
        #game-page.players-5 .opponents {
            display: flex;
            gap: 7px;
            padding: 4px;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scroll-snap-type: x proximity;
            scroll-padding: 4px;
        }

        .players-5 .opponents > :global(.four-opponents) {
            flex: 0 0 160px;
            scroll-snap-align: start;
        }
    }

    @media (min-width: 600px) and (max-width: 1023px) {
        .players-5 #game-board {
            gap: 12px;
        }

        .players-5 :global(.local .discard-cards) {
            max-width: 260px;
        }

        .players-5 :global(.local .hand) {
            max-width: 490px;
        }
        .players-5 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-5 .side-pile,
        .players-5 .building-piles,
        .players-5 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-5 .side-pile {
            grid-column: 1;
        }


        .players-5 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-5 .table-stack,
        .players-5 .side-pile > :global(.game-card),
        .players-5 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-5 .side-pile p,
        .players-5 .building-pile span {
            grid-row: 3;
        }

        .players-5 .side-pile h2,
        .players-5 .side-pile p {
            font-size: 10px;
        }
    }
    @media (min-width: 1024px) {
        #game-page.players-5 {
            width: 95%;
            max-width: 1520px;
            grid-template-columns: minmax(0, 1fr) minmax(230px, 23%);
            align-content: start;
            align-items: stretch;
            padding: 16px;
        }

        .players-5 #game-board {
            gap: 12px;
        }

        .players-5 #desktop-chat {
            position: static;
            height: auto;
            align-self: stretch;
            contain: size;
            overflow: hidden;
        }

        .players-5 #chat-container {
            height: 100%;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .players-5 #shared-table {
            padding: 14px;
        }

        .players-5 #shared-table h1 {
            font-size: 18px;
        }

        .players-5 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-5 .side-pile,
        .players-5 .building-piles,
        .players-5 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-5 .side-pile {
            grid-column: 1;
        }


        .players-5 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-5 .table-stack,
        .players-5 .side-pile > :global(.game-card),
        .players-5 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-5 .side-pile p,
        .players-5 .building-pile span {
            grid-row: 3;
        }

        .players-5 .side-pile h2,
        .players-5 .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-5 {
            max-width: 1640px;
            padding: 26px;
        }

        .players-5 #game-board {
            gap: 18px;
        }

        .players-5 #shared-table {
            padding: 18px;
        }

        .players-5 #shared-table h1 {
            font-size: 19px;
        }

        .players-5 .table-piles {
            max-width: 580px;
        }

        .players-5 #game-room span,
        .players-5 #game-room strong {
            font-size: 16px;
        }

        .players-5 #game-room small {
            font-size: 13px;
        }
    }

    @media (min-width: 1920px) {
        #game-page.players-5 {
            max-width: 1800px;
            padding: 32px;
        }
    }
    @media (min-width: 1024px) {
        .players-5 #game-room {
            display: flex;
            flex-direction: column;
            justify-content: center;
            min-height: 50px;
            padding: 4px 7px;
            background: var(--comic-cream);
            border: 3px solid var(--comic-ink);
            border-radius: 5px;
            box-shadow: 3px 3px 0 var(--comic-ink);
            text-align: left;
            overflow-wrap: anywhere;
        }

        .players-5 #game-room span,
        .players-5 #game-room strong {
            font-size: 14px;
        }

        .players-5 #game-room small {
            font-size: 12px;
        }

        .players-5 #game-header {
            grid-template-columns: auto minmax(0, 1fr);
        }

        #game-page.players-5 .opponents {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            align-items: stretch;
            gap: 6px;
            padding: 4px;
            overflow: visible;
            scroll-snap-type: none;
        }

        .players-5 .opponents > :global(.four-opponents) {
            min-width: 0;
            scroll-snap-align: none;
        }
    }

    @media (min-width: 1200px) {
        #game-page.players-5 .opponents {
            gap: 8px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-5 .opponents {
            gap: 10px;
        }

        .players-5 #game-room span,
        .players-5 #game-room strong {
            font-size: 16px;
        }

        .players-5 #game-room small {
            font-size: 13px;
        }
    }
    @media (min-width: 1024px) {
        .players-5 #chat-messages {
            overflow-x: hidden;
        }
    }
    @media (max-width: 1023px) {
        #game-page.players-6 .opponents {
            display: flex;
            gap: 7px;
            padding: 4px;
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-x: contain;
            scroll-snap-type: x proximity;
            scroll-padding: 4px;
        }

        .players-6 .opponents > :global(.five-opponents) {
            flex: 0 0 160px;
            scroll-snap-align: start;
        }
    }

    @media (min-width: 600px) and (max-width: 1023px) {
        .players-6 #game-board {
            gap: 12px;
        }

        .players-6 :global(.local .discard-cards) {
            max-width: 260px;
        }

        .players-6 :global(.local .hand) {
            max-width: 490px;
        }
        .players-6 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-6 .side-pile,
        .players-6 .building-piles,
        .players-6 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-6 .side-pile {
            grid-column: 1;
        }


        .players-6 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-6 .table-stack,
        .players-6 .side-pile > :global(.game-card),
        .players-6 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-6 .side-pile p,
        .players-6 .building-pile span {
            grid-row: 3;
        }

        .players-6 .side-pile h2,
        .players-6 .side-pile p {
            font-size: 10px;
        }
    }
    @media (min-width: 1024px) {
        #game-page.players-6 {
            width: 95%;
            max-width: 1520px;
            grid-template-columns: minmax(0, 1fr) minmax(210px, 20%);
            align-content: start;
            align-items: stretch;
            padding: 16px;
        }

        .players-6 #game-board {
            gap: 12px;
        }

        .players-6 #desktop-chat {
            position: static;
            height: auto;
            align-self: stretch;
            contain: size;
            overflow: hidden;
        }

        .players-6 #chat-container {
            height: 100%;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .players-6 #shared-table {
            padding: 14px;
        }

        .players-6 #shared-table h1 {
            font-size: 18px;
        }

        .players-6 .table-piles {
            max-width: 550px;
            grid-template-rows: auto auto auto;
            align-items: start;
            row-gap: 0;
        }

        .players-6 .side-pile,
        .players-6 .building-piles,
        .players-6 .building-pile {
            display: grid;
            grid-template-rows: subgrid;
            grid-row: 1 / 4;
            align-items: start;
            row-gap: 0;
        }

        .players-6 .side-pile {
            grid-column: 1;
        }


        .players-6 .building-piles {
            grid-column: 2;
            padding-top: 0;
        }

        .players-6 .table-stack,
        .players-6 .side-pile > :global(.game-card),
        .players-6 .building-pile > :global(.game-card) {
            grid-row: 2;
        }

        .players-6 .side-pile p,
        .players-6 .building-pile span {
            grid-row: 3;
        }

        .players-6 .side-pile h2,
        .players-6 .side-pile p {
            font-size: 10px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-6 {
            max-width: 1640px;
            padding: 26px;
        }

        .players-6 #game-board {
            gap: 18px;
        }

        .players-6 #shared-table {
            padding: 18px;
        }

        .players-6 #shared-table h1 {
            font-size: 19px;
        }

        .players-6 .table-piles {
            max-width: 580px;
        }

        .players-6 #game-room span,
        .players-6 #game-room strong {
            font-size: 16px;
        }

        .players-6 #game-room small {
            font-size: 13px;
        }
    }

    @media (min-width: 1920px) {
        #game-page.players-6 {
            max-width: 1800px;
            padding: 32px;
        }
    }
    @media (min-width: 1024px) {
        .players-6 #game-room {
            display: flex;
            flex-direction: column;
            justify-content: center;
            min-height: 50px;
            padding: 4px 7px;
            background: var(--comic-cream);
            border: 3px solid var(--comic-ink);
            border-radius: 5px;
            box-shadow: 3px 3px 0 var(--comic-ink);
            text-align: left;
            overflow-wrap: anywhere;
        }

        .players-6 #game-room span,
        .players-6 #game-room strong {
            font-size: 14px;
        }

        .players-6 #game-room small {
            font-size: 12px;
        }

        .players-6 #game-header {
            grid-template-columns: auto minmax(0, 1fr);
        }

        #game-page.players-6 .opponents {
            display: grid;
            grid-template-columns: repeat(5, minmax(0, 1fr));
            align-items: stretch;
            gap: 4px;
            padding: 4px;
            overflow: visible;
            scroll-snap-type: none;
        }

        .players-6 .opponents > :global(.five-opponents) {
            min-width: 0;
            scroll-snap-align: none;
        }
    }

    @media (min-width: 1200px) {
        #game-page.players-6 .opponents {
            gap: 6px;
        }
    }

    @media (min-width: 1440px) {
        #game-page.players-6 .opponents {
            gap: 8px;
        }

        .players-6 #game-room span,
        .players-6 #game-room strong {
            font-size: 16px;
        }

        .players-6 #game-room small {
            font-size: 13px;
        }
    }
    @media (min-width: 1024px) {
        .players-6 #chat-messages {
            overflow-x: hidden;
        }
    }
    #game-page #game-header {
        grid-template-columns: auto minmax(0, 1fr) auto 44px;
    }

    #game-page #game-room span {
        min-width: 0;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    #sound-dialog {
        box-sizing: border-box;
        width: min(340px, calc(100vw - 24px));
        max-height: calc(100dvh - 32px);
        margin: auto;
        padding: 16px;
        overflow-y: auto;
        overflow-x: hidden;
        color: var(--comic-ink);
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        border-radius: 8px;
        box-shadow: 5px 5px 0 var(--comic-ink);
    }
    #sound-dialog::backdrop { background: rgb(0 0 0 / 55%); }
    .sound-heading, .sound-setting { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .sound-heading h2 { margin: 0; }
    #sound-dialog h3 { margin: 12px 0 4px; font-size: 14px; }
    .sound-setting { margin: 6px 0; font-weight: 700; font-size: 14px; }
    #sound-dialog button { min-width: 44px; min-height: 44px; padding: 4px 8px; border: 2px solid var(--comic-ink); border-radius: 4px; box-shadow: 2px 2px 0 var(--comic-ink); background: var(--comic-paper-white); color: var(--comic-ink); font-weight: 900; }
    #sound-dialog button[aria-pressed="true"] { background: #B8FF3A; }
    #sound-dialog .mute-all { width: 100%; margin-top: 12px; }

    .audio-controls {
        display: flex;
        gap: 4px;
    }

    .audio-controls button {
        display: grid;
        place-items: center;
        width: 36px;
        height: 36px;
        padding: 0;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        border-radius: 4px;
        box-shadow: 2px 2px 0 var(--comic-ink);
        font-size: 18px;
        line-height: 1;
    }

    @media (min-width: 1024px) {
        #game-page #game-header {
            grid-template-columns: auto minmax(0, 1fr) auto;
        }
    }
</style>
