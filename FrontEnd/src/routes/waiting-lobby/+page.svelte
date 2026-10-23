<script lang="ts">
    import { onMount, tick } from 'svelte';
    import { goto } from '$app/navigation';
    import { avatars } from '#lib/data/avatars.ts';
    import { store } from '#lib/state/store.svelte.ts';
    import {
        roomSocketStore,
        connectToRoom,
        disconnectFromRoom,
        releaseRoomConnection,
        startGame,
        updateRoomUsername,
        updateRoomAvatar,
        toggleRoomReady,
        sendChatMessage,
        leaveRoom
    } from '#lib/state/roomSocket.svelte.ts';

    let chatDialog: HTMLDialogElement;
    let chatMessagesElement: HTMLDivElement;
    let chatInput = $state('');
    let mobileChat = $state(false);
    let chatInitialized = $state(false);
    let lastSeenMessageId = $state<string | undefined>();
    let chatHasUnread = $state(false);
    let copyStatus = $state('');
    let viewportHeight = $state('100dvh');
    const emojis = ['😀', '😂', '❤️', '👍', '🎉', '😎'];
    const lastMessageId = $derived(roomSocketStore.room?.chatMessages.at(-1)?.id);

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
            chatHasUnread = false;
            return;
        }
        if (!mobileChat) chatHasUnread = false;
        if (latestId === lastSeenMessageId) return;

        lastSeenMessageId = latestId;
        chatHasUnread = !!latestId && mobileChat && !chatDialog?.open;
    });

    $effect(() => {
        if (roomSocketStore.unavailable && chatDialog?.open) chatDialog.close();
    });

    let gameNavigationPending = false;
    $effect(() => {
        const room = roomSocketStore.room;
        const game = roomSocketStore.game;
        if (!gameNavigationPending && (room?.state === 'IN_GAME' || game)) {
            const roomId = game?.roomId ?? room?.roomId;
            if (!roomId) return;
            gameNavigationPending = true;
            void goto(`/game?roomId=${encodeURIComponent(roomId)}`).catch(() => {
                gameNavigationPending = false;
            });
        }
    });

    function markChatRead() {
        chatHasUnread = false;
        lastSeenMessageId = lastMessageId;
    }

    async function scrollChatToBottom() {
        await tick();
        if (chatMessagesElement) chatMessagesElement.scrollTop = chatMessagesElement.scrollHeight;
    }

    function openChat() {
        chatDialog.showModal();
        markChatRead();
        void scrollChatToBottom();
    }

    function submitChat(event: SubmitEvent) {
        event.preventDefault();
        if (sendChatMessage(chatInput)) chatInput = '';
    }

    async function copyLink() {
        try {
            await navigator.clipboard.writeText(window.location.href);
            copyStatus = 'Link copied!';
        } catch {
            copyStatus = 'Could not copy link. Copy it from the address bar.';
        }
    }
    const currentPlayer = $derived(
        roomSocketStore.room?.players.find((player) => player.id === store.player.id)
    );

    function handleUsernameInput(event: Event) {
        const input = event.currentTarget as HTMLInputElement;
        const username = input.value.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 8);
        input.value = username;
        store.player.username = username;
    }

    function changeUsername() {
        if (currentPlayer?.ready) return;
        if (!store.player.username) store.player.username = 'user';
        updateRoomUsername(store.player.username);
    }

    function changeAvatar(avatar: number) {
        if (!currentPlayer?.ready) updateRoomAvatar(avatar);
    }

    onMount(() => {
        const desktopLayout = window.matchMedia('(min-width: 1024px)');
        function updateChatLayout() {
            mobileChat = !desktopLayout.matches;
            markChatRead();
        }
        updateChatLayout();
        desktopLayout.addEventListener('change', updateChatLayout);
        return () => desktopLayout.removeEventListener('change', updateChatLayout);
    });

    onMount(() => {
        // The visual viewport also shrinks when a mobile keyboard opens.
        const viewport = window.visualViewport;
        function updateViewport() {
            viewportHeight = `${viewport?.height ?? window.innerHeight}px`;
        }
        updateViewport();
        viewport?.addEventListener('resize', updateViewport);
        return () => viewport?.removeEventListener('resize', updateViewport);
    });

    onMount(() => {
        const roomId = new URLSearchParams(window.location.search).get('roomId');
        if (!roomId) {
            roomSocketStore.unavailable = true;
            roomSocketStore.error = 'INVALID ROOM';
            return disconnectFromRoom;
        }
        void connectToRoom(roomId);
        return releaseRoomConnection;
    });
</script>

<main id="lobby-page" class:is-host={currentPlayer?.host ?? false}>

    <section id="lobby-content">

        <header id="lobby-header">

            <button
                id="leave-button"
                type="button"
                onclick={leaveRoom}
                disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || !currentPlayer || roomSocketStore.leavePending}
            >
                LEAVE
            </button>

            <h1 id="lobby-title">
                WAITING LOBBY
            </h1>

            <button
                id="chat-button"
                type="button"
                class:has-unread-chat={chatHasUnread}
                aria-label={chatHasUnread ? 'Open chat, new message' : 'Open chat'}
                onclick={openChat}
            >
                💬
            </button>

        </header>


        <section id="room-info">

            <div id="room-code-row">

                <span>
                    ROOM CODE
                </span>

                <strong id="room-code">
                    {roomSocketStore.room?.roomCode ?? '--------'}
                </strong>

                <button
                    id="copy-link-button"
                    type="button"
                    onclick={copyLink}
                >
                    COPY LINK
                </button>

            </div>

            <span role="status">{copyStatus}</span>

            {#if roomSocketStore.error}
                <p role="alert">{roomSocketStore.error}</p>
            {/if}

            {#if roomSocketStore.unavailable}
                <button class="return-button" type="button" onclick={() => goto('/find-game')}>FIND GAME</button>
                <button class="return-button" type="button" onclick={() => goto('/')}>HOME</button>
            {/if}

            <div id="game-name">
                <span>GAME:</span>
                <strong>{roomSocketStore.room?.roomName ?? 'LOADING'}</strong>
            </div>

        </section>


        <section id="profile-panel">

            <label for="lobby-username">
                YOUR NAME
            </label>

            <div id="lobby-name-box">

                <input
                    id="lobby-username"
                    type="text"
                    maxlength="8"
                    bind:value={store.player.username}
                    oninput={handleUsernameInput}
                    onchange={changeUsername}
                    disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending || !currentPlayer || currentPlayer.ready}
                >

                <span id="lobby-pencil">
                    ✎
                </span>

            </div>


            <h2>
                CHOOSE YOUR AVATAR
            </h2>

            <div id="lobby-avatar-grid">

                {#each avatars as avatar, index}

                    <button
                        class:selected-avatar={store.player.avatar === index + 1}
                        onclick={() => changeAvatar(index + 1)}
                        disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending || !currentPlayer || currentPlayer.ready}
                        class="lobby-avatar"
                        type="button"
                    >
                        <img
                            src={avatar}
                            alt={`Avatar ${index + 1}`}
                        >
                    </button>

                {/each}

            </div>

        </section>


        <section id="players-list">
            {#each roomSocketStore.room?.players ?? [] as player (player.id)}
                <div class="player-row">
                    <img
                        class="player-avatar"
                        src={avatars[player.avatar - 1] ?? avatars[0]}
                        alt={player.username}
                    >
                    <span class="player-name">
                        <span class="player-username">{player.username}</span>
                        <span class="player-tag">#{player.id.substring(0, 3)}</span>
                    </span>
                    <div class="player-status">
                        {#if player.host}
                            <span class="host-badge">HOST</span>
                        {/if}
                        {#if player.ready}
                            <span class="ready-badge">READY</span>
                        {:else}
                            <span class="not-ready-badge">WAITING</span>
                        {/if}
                    </div>
                </div>
            {/each}
        </section>


        <footer id="lobby-actions">

            <button
                id="ready-button"
                type="button"
                onclick={toggleRoomReady}
                disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || !currentPlayer || roomSocketStore.readyPending || roomSocketStore.leavePending}
                aria-pressed={currentPlayer?.ready ?? false}
            >
                {currentPlayer?.ready ? 'NOT READY' : 'READY'}
            </button>

            <button
                id="start-game-button"
                type="button"
                onclick={startGame}
                disabled={!currentPlayer?.host || !roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.startPending || !roomSocketStore.room || roomSocketStore.room.state !== 'WAITING' || roomSocketStore.room.players.length < 2 || roomSocketStore.room.players.length > 6 || !roomSocketStore.room.players.every((player) => player.ready) || (roomSocketStore.room.playerTarget !== 1 && roomSocketStore.room.players.length !== roomSocketStore.room.playerTarget)}
            >
                START GAME
            </button>

        </footer>

    </section>


    <dialog id="chat-dialog" onclose={markChatRead} aria-label="Room chat" bind:this={chatDialog} style:--chat-viewport-height={viewportHeight}>

        <div id="chat-container">

            <header id="chat-header">

                <h2>
                    ROOM CHAT
                </h2>

                <button
                    id="close-chat-button"
                    type="button"
                    aria-label="Close chat"
                    onclick={() => chatDialog.close()}
                >
                    ✕
                </button>

            </header>


            <div id="chat-messages" bind:this={chatMessagesElement}>
                {#each roomSocketStore.room?.chatMessages ?? [] as message (message.id)}
                    <div class="chat-message" id={`message-${message.id}`}>
                        <img
                            src={avatars[message.avatar - 1] ?? avatars[0]}
                            alt={message.username}
                        >
                        <div>
                            <strong>{message.username}</strong>
                            <p>{message.message}</p>
                        </div>
                    </div>
                {/each}
            </div>


            <form id="chat-form" onsubmit={submitChat}>

                <input
                    id="chat-input"
                    type="text"
                    placeholder="Type a message..."
                    aria-label="Chat message"
                    bind:value={chatInput}
                    maxlength="200"
                    enterkeyhint="send"
                    disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending}
                    autocomplete="off"
                >

                <button
                    id="send-chat-button"
                    type="submit"
                    disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending || !chatInput.trim()}
                >
                    SEND
                </button>

                <div class="chat-emojis" aria-label="Add an emoji">
                    {#each emojis as emoji}
                        <button type="button" aria-label={`Add ${emoji}`} disabled={!roomSocketStore.connected || roomSocketStore.reconnecting || roomSocketStore.leavePending || chatInput.length + emoji.length > 200} onclick={() => chatInput += emoji}>{emoji}</button>
                    {/each}
                </div>
            </form>

        </div>

    </dialog>

</main>


<style>

    :global(body) {
        width: 100%;
        min-height: 100svh;

        overflow-x: hidden;
        overflow-y: hidden;

        background:
            radial-gradient(
                circle,
                rgba(16, 25, 31, 0.18) 1px,
                transparent 1.5px
            ),
            var(--comic-blue);

        background-size: 7px 7px;
    }


    #lobby-page {
        width: 100%;
        max-width: 430px;

        height: 100svh;

        margin: 0 auto;

        padding:
            max(6px, env(safe-area-inset-top))
            8px
            max(6px, env(safe-area-inset-bottom));

        overflow: hidden;
    }


    #lobby-content {
        width: 100%;
        height: 100%;

        display: grid;

        grid-template-rows:
            auto
            auto
            auto
            minmax(0, 1fr)
            auto;

        gap: 5px;
    }


    #lobby-header {
        display: grid;

        grid-template-columns:
            auto
            minmax(0, 1fr)
            38px;

        align-items: center;

        gap: 5px;
    }


    #leave-button,
    #chat-button,
    #lobby-title {
        min-height: 36px;

        background:
            var(--comic-paper-white);

        border:
            3px solid
            var(--comic-ink);

        border-radius: 5px;

        box-shadow:
            3px
            3px
            0
            var(--comic-ink);
    }


    #leave-button {
        padding: 0 9px;

        font-size: 12px;
        font-weight: 900;
    }


    #lobby-title {
        display: flex;

        align-items: center;
        justify-content: center;

        min-width: 0;

        padding: 0 6px;

        font-size:
            clamp(
                15px,
                4.8vw,
                20px
            );

        font-weight: 900;
        font-style: italic;

        text-align: center;

        white-space: nowrap;
    }


    @media (max-width: 1023.98px) {
        #chat-button.has-unread-chat {
            background: var(--comic-yellow);
        }
    }

    .return-button {
        min-height: 42px;
        padding: 0 12px;
        background: var(--comic-lightgreen);
        border: 3px solid var(--comic-ink);
        font-weight: 900;
        font-style: italic;
    }

    #chat-button {
        padding: 0;

        font-size: 18px;
    }


    #room-info {
        display: flex;

        flex-direction: column;

        gap: 5px;
    }


    #room-code-row,
    #game-name {
        background:
            var(--comic-paper-white);

        border:
            3px solid
            var(--comic-ink);

        border-radius: 5px;
    }


    #room-code-row {
        min-height: 40px;

        display: grid;

        grid-template-columns:
            auto
            minmax(0, 1fr)
            auto;

        align-items: center;

        gap: 5px;

        padding: 4px 7px;

        font-size: 10px;
        font-weight: 900;
    }


    #room-code {
        min-width: 0;

        font-size: 15px;

        text-align: center;
    }


    #copy-link-button {
        min-height: 28px;

        padding: 0 7px;

        background:
            var(--comic-lightgreen);

        border:
            2px solid
            var(--comic-ink);

        font-size: 9px;
        font-weight: 900;
    }


    #game-name {
        display: grid;
        grid-template-columns: minmax(60px, 1fr) minmax(0, 3fr) minmax(60px, 1fr);
        align-items: center;
        gap: 10px;

        padding: 6px 7px;

        font-size: 12px;
        font-weight: 400;

        text-align: left;
    }


    #game-name strong {
        min-width: 0;
        font-weight: 900;
        text-align: center;
        overflow-wrap: anywhere;
    }


    #profile-panel {
        min-width: 0;

        padding:
            8px
            12px
            10px;

        margin-bottom: 1px;

        background:
            var(--comic-cream);

        border:
            3px solid
            var(--comic-ink);

        border-radius: 16px;

        box-shadow:
            4px
            4px
            0
            var(--comic-ink);
    }


    #profile-panel label,
    #profile-panel h2 {
        display: block;

        margin-bottom: 4px;

        font-size: 13px;
        font-weight: 900;
        font-style: italic;
    }


    #profile-panel h2 {
        margin-top: 6px;
    }


    #lobby-name-box {
        position: relative;
    }


    #lobby-username {
        width: 100%;
        height: 34px;

        padding:
            0
            36px
            0
            9px;

        background:
            var(--comic-paper-white);

        border:
            2px solid
            var(--comic-ink);

        font-size: 16px;
    }


    #lobby-pencil {
        position: absolute;

        right: 9px;
        top: 5px;

        font-size: 18px;

        transform:
            rotate(-10deg);

        pointer-events: none;
    }


    #lobby-avatar-grid {
        display: grid;

        grid-template-columns:
            repeat(5, minmax(0, 1fr));

        gap: 4px;
    }


    .lobby-avatar {
        min-width: 0;

        padding: 0;

        aspect-ratio: 1;

        overflow: hidden;

        background:
            var(--comic-blue);

        border:
            2px solid
            var(--comic-ink);

        border-radius: 4px;
    }


    .lobby-avatar img {
        width: 100%;
        height: 100%;

        object-fit: cover;
    }


    .selected-avatar {
        border:
            4px solid
            var(--comic-purple);
    }


    #players-list {
        min-height: 0;

        display: flex;

        flex-direction: column;

        gap: 4px;
    }


    .player-row {
        width: 100%;
        height: 36px;

        display: grid;

        grid-template-columns:
            24px
            minmax(0, 1fr)
            auto;

        align-items: center;

        gap: 6px;

        padding: 3px 7px;

        background:
            var(--comic-paper-white);

        border:
            2px solid
            var(--comic-ink);

        border-radius: 4px;

        font-size: 13px;
        font-weight: 900;

        overflow: hidden;
    }


    .player-avatar {
        width: 22px;
        height: 22px;

        object-fit: cover;

        border:
            2px solid
            var(--comic-ink);

        border-radius: 4px;
    }


    .player-name {
        display: flex;
        align-items: baseline;
        gap: 5px;
        min-width: 0;
    }


    .player-username {
        min-width: 0;

        overflow: hidden;

        white-space: nowrap;

        text-overflow: ellipsis;
    }


    .player-tag {
        flex-shrink: 0;
        color: #687078;
        font-size: 0.85em;
        font-weight: 600;
    }


    .player-status {
        min-width: 0;

        display: flex;

        align-items: center;

        gap: 4px;
    }


    .host-badge {
        padding: 2px 5px;

        background:
            var(--comic-yellow);

        border:
            2px solid
            var(--comic-ink);

        font-size: 8px;

        line-height: 1;
    }


    .ready-badge {
        color:
            var(--comic-green);

        font-size: 10px;

        line-height: 1;
    }


    .not-ready-badge {
        color:
            var(--comic-ink-soft);

        font-size: 9px;

        line-height: 1;
    }


    #lobby-actions {
        display: grid;

        grid-template-columns:
            1fr;

        gap: 6px;
    }


    #ready-button,
    #start-game-button {
        min-height: 42px;

        border:
            3px solid
            var(--comic-ink);

        box-shadow:
            4px
            4px
            0
            var(--comic-ink);

        font-size: 16px;
        font-weight: 900;
        font-style: italic;
    }


    #ready-button {
        background:
            var(--comic-lightgreen);
    }


    #start-game-button {
        display: none;

        background:
            var(--comic-yellow);
    }


    .is-host #lobby-actions {
        grid-template-columns:
            1fr
            1.35fr;
    }


    .is-host #start-game-button {
        display: block;
    }


    #start-game-button:disabled {
        background: #b6a34f;

        color:
            var(--comic-ink-soft);

        opacity: 0.7;
    }


    #chat-dialog {
        width:
            calc(100% - 20px);

        max-width: 400px;

        max-height:
            calc(var(--chat-viewport-height, 100dvh) - 30px);

        margin: auto;

        padding: 0;

        overflow: hidden;

        background:
            var(--comic-cream);

        border:
            3px solid
            var(--comic-ink);

        border-radius: 8px;

        box-shadow:
            6px
            6px
            0
            var(--comic-ink);
    }


    #chat-dialog::backdrop {
        background:
            rgba(16, 25, 31, 0.7);
    }


    #chat-container {
        height:
            min(
                520px,
                calc(var(--chat-viewport-height, 100dvh) - 30px)
            );

        display: grid;

        grid-template-rows:
            auto
            minmax(0, 1fr)
            auto;
    }


    #chat-header {
        display: flex;

        align-items: center;
        justify-content: space-between;

        padding: 9px 10px;

        background:
            var(--comic-yellow);

        border-bottom:
            3px solid
            var(--comic-ink);
    }


    #chat-header h2 {
        font-size: 18px;
        font-weight: 900;
        font-style: italic;
    }


    #close-chat-button {
        width: 32px;
        height: 32px;

        background:
            var(--comic-red);

        border:
            2px solid
            var(--comic-ink);

        font-size: 16px;
        font-weight: 900;
    }


    #chat-messages {
        min-height: 0;

        overflow-y: auto;

        padding: 10px;

        display: flex;

        flex-direction: column;

        gap: 10px;
    }


    .chat-message {
        flex-shrink: 0;
        overflow-wrap: anywhere;
        display: grid;

        grid-template-columns:
            34px
            minmax(0, 1fr);

        gap: 7px;

        align-items: start;
    }


    .chat-message img {
        width: 34px;
        height: 34px;

        object-fit: cover;

        border:
            2px solid
            var(--comic-ink);

        border-radius: 4px;
    }


    .chat-message strong {
        font-size: 13px;
    }


    .chat-message p {
        margin-top: 2px;

        font-size: 13px;
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
        font-size: 20px;
    }

    #chat-messages {
        overscroll-behavior: contain;
    }

    #chat-form {
        display: grid;

        grid-template-columns:
            minmax(0, 1fr)
            auto;

        gap: 5px;

        padding: 9px;

        border-top:
            3px solid
            var(--comic-ink);
    }


    #chat-input {
        min-width: 0;
        height: 38px;

        padding: 0 9px;

        background:
            var(--comic-paper-white);

        border:
            2px solid
            var(--comic-ink);

        font-size: 16px;
    }


    #send-chat-button {
        min-height: 38px;

        padding: 0 10px;

        background:
            var(--comic-blue);

        border:
            2px solid
            var(--comic-ink);

        font-size: 12px;
        font-weight: 900;
    }


    @media (hover: hover) and (pointer: fine) {

        .lobby-avatar {
            cursor: pointer;

            transition:
                transform 0.15s ease,
                filter 0.15s ease,
                box-shadow 0.15s ease;
        }


        .lobby-avatar:not(.selected-avatar):hover {
            transform:
                translateY(-3px)
                scale(1.08)
                rotate(-2deg);

            filter:
                brightness(1.1);
        }


        #leave-button,
        #chat-button,
        #copy-link-button,
        #ready-button,
        #start-game-button,
        #send-chat-button {
            cursor: pointer;
        }


        #ready-button:hover {
            filter:
                brightness(1.08);

            transform:
                translateY(-2px);
        }


        #start-game-button:not(:disabled):hover {
            filter:
                brightness(1.08);

            transform:
                translateY(-2px);
        }
    }


    @media (min-width: 600px) {

        #lobby-page {
            max-width: 560px;

            padding:
                12px
                14px;
        }


        #lobby-content {
            gap: 7px;
        }


        #lobby-header {
            grid-template-columns:
                auto
                minmax(0, 1fr)
                46px;
        }


        #leave-button,
        #chat-button,
        #lobby-title {
            min-height: 42px;
        }


        #lobby-title {
            font-size: 22px;
        }


        #room-code-row {
            min-height: 46px;

            font-size: 12px;
        }


        #room-code {
            font-size: 18px;
        }


        #game-name {
            font-size: 14px;
        }


        #profile-panel {
            padding:
                11px
                16px
                13px;
        }


        #lobby-username {
            height: 40px;

            font-size: 17px;
        }


        .player-row {
            height: 40px;

            grid-template-columns:
                28px
                minmax(0, 1fr)
                auto;

            font-size: 14px;
        }


        .player-avatar {
            width: 26px;
            height: 26px;
        }


        #ready-button,
        #start-game-button {
            min-height: 48px;

            font-size: 18px;
        }
    }


    @media (min-width: 1024px) {

        :global(body) {
            height: auto;

            min-height: 100svh;

            overflow-x: hidden;
            overflow-y: auto;
        }


        #lobby-page {
            width: 94%;
            max-width: 1355px;

            height: auto;

            margin: 0 auto;

            padding:
                21px
                24px;

            display: grid;

            grid-template-columns:
                minmax(0, 2fr)
                minmax(314px, 1fr);

            align-items: stretch;

            gap: 21px;

            overflow: visible;
        }


        #lobby-content {
            width: 100%;
            height: auto;

            min-width: 0;

            display: grid;

            grid-template-columns:
                minmax(0, 1fr)
                minmax(0, 1fr);

            grid-template-areas:
                "header header"
                "room room"
                "profile players"
                "actions actions";

            grid-template-rows:
                auto
                auto
                auto
                auto;

            align-content: start;

            gap: 9px;
        }


        #lobby-header {
            grid-area: header;

            grid-template-columns:
                127px
                minmax(0, 1fr);

            gap: 9px;
        }


        #chat-button {
            display: none;
        }


        #leave-button,
        #lobby-title {
            min-height: 50px;
        }


        #leave-button {
            font-size: 16px;
        }


        #lobby-title {
            font-size: 29px;
        }


        #room-info {
            grid-area: room;
        }


        #room-code-row {
            min-height: 50px;

            padding:
                5px
                11px;

            font-size: 13px;
        }


        #room-code {
            font-size: 22px;
        }


        #copy-link-button {
            min-height: 37px;

            font-size: 11px;
        }


        #game-name {
            padding:
                7px
                11px;

            font-size: 16px;
        }


        #profile-panel {
            grid-area: profile;

            align-self: start;

            margin: 0;

            padding:
                11px
                16px
                13px;
        }


        #profile-panel label,
        #profile-panel h2 {
            font-size: 16px;
        }


        #lobby-username {
            height: 44px;

            font-size: 19px;
        }


        #lobby-avatar-grid {
            grid-template-columns:
                repeat(5, minmax(0, 1fr));

            gap: 5px;
        }


        #players-list {
            grid-area: players;

            min-width: 0;

            display: flex;

            flex-direction: column;

            align-self: start;

            gap: 6px;
        }


        .player-row {
            height: 50px;

            grid-template-columns:
                35px
                minmax(0, 1fr)
                auto;

            padding:
                4px
                9px;

            font-size: 16px;
        }


        .player-avatar {
            width: 32px;
            height: 32px;
        }


        .ready-badge {
            font-size: 12px;
        }


        .not-ready-badge {
            font-size: 11px;
        }


        .host-badge {
            font-size: 10px;
        }


        #lobby-actions {
            grid-area: actions;

            margin-top: 2px;
        }


        #ready-button,
        #start-game-button {
            min-height: 57px;

            font-size: 22px;
        }


        #chat-dialog {
            position: static;

            width: 100%;
            max-width: none;

            /* Let the lobby column determine the shared grid row height. */
            contain: size;
            height: auto;
            min-height: 0;

            max-height: none;

            align-self: stretch;

            margin: 0;

            padding: 0;

            display: block;

            background:
                var(--comic-cream);

            border:
                3px solid
                var(--comic-ink);

            border-radius: 9px;

            box-shadow:
                5px
                5px
                0
                var(--comic-ink);
        }


        #chat-dialog::backdrop {
            display: none;
        }


        #chat-container {
            width: 100%;
            height: 100%;

            min-height: 0;

            display: grid;

            grid-template-rows:
                auto
                minmax(0, 1fr)
                auto;
        }


        #chat-header {
            min-height: 50px;

            padding:
                9px
                13px;
        }


        #chat-header h2 {
            font-size: 23px;
        }


        #close-chat-button {
            display: none;
        }


        #chat-messages {
            min-height: 0;

            padding: 13px;

            gap: 13px;
        }


        .chat-message {
            grid-template-columns:
                41px
                minmax(0, 1fr);
        }


        .chat-message img {
            width: 41px;
            height: 41px;
        }


        .chat-message strong,
        .chat-message p {
            font-size: 15px;
        }


        #chat-form {
            padding: 11px;
        }


        #chat-input {
            height: 46px;

            font-size: 18px;
        }


        #send-chat-button {
            font-size: 14px;

            min-height: 46px;

            padding:
                0
                13px;
        }
    }


    @media (min-width: 1440px) {

        #lobby-page {
            max-width: 1523px;

            grid-template-columns:
                minmax(0, 2.2fr)
                minmax(370px, 0.8fr);

            gap: 27px;

            padding:
                27px
                31px;
        }


        #lobby-title {
            font-size: 31px;
        }


        #room-code {
            font-size: 24px;
        }


        #profile-panel {
            padding:
                13px
                20px
                16px;
        }


        #profile-panel label,
        #profile-panel h2 {
            font-size: 17px;
        }


        #lobby-username {
            height: 45px;

            font-size: 20px;
        }


        .player-row {
            height: 52px;

            grid-template-columns:
                36px
                minmax(0, 1fr)
                auto;

            font-size: 17px;
        }


        .player-avatar {
            width: 34px;
            height: 34px;
        }


        #ready-button,
        #start-game-button {
            min-height: 60px;

            font-size: 24px;
        }


        #chat-header h2 {
            font-size: 25px;
        }
    }


    @media (min-width: 1920px) {

        #lobby-page {
            max-width: 1680px;

            grid-template-columns:
                minmax(0, 2.3fr)
                448px;

            gap: 34px;

            padding:
                34px
                40px;
        }


        #lobby-title {
            font-size: 34px;
        }


        #room-code {
            font-size: 26px;
        }


        #game-name {
            font-size: 18px;
        }


        #profile-panel label,
        #profile-panel h2 {
            font-size: 18px;
        }


        #lobby-username {
            height: 47px;

            font-size: 20px;
        }


        .player-row {
            height: 54px;

            grid-template-columns:
                38px
                minmax(0, 1fr)
                auto;

            font-size: 18px;
        }


        .player-avatar {
            width: 36px;
            height: 36px;
        }


        #ready-button,
        #start-game-button {
            min-height: 63px;

            font-size: 26px;
        }
    }


    @media (min-width: 2560px) {

        #lobby-page {
            max-width: 1848px;

            grid-template-columns:
                minmax(0, 2.4fr)
                493px;

            gap: 38px;
        }


        #lobby-title {
            font-size: 36px;
        }


        #profile-panel {
            padding:
                16px
                22px
                18px;
        }


        .player-row {
            height: 56px;

            font-size: 19px;
        }


        #ready-button,
        #start-game-button {
            min-height: 67px;

            font-size: 27px;
        }
    }

</style>