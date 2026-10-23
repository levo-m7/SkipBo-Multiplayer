<script lang="ts">
    import { onMount, tick } from 'svelte';
    import GameCard from '#lib/components/game/GameCard.svelte';
    import { avatars } from '#lib/data/avatars.ts';
    import { cardValue, type LocalGamePlayer, type OpponentGamePlayer, type SelectedSource } from '#lib/types/game.ts';

    type Props = {
        player: LocalGamePlayer | OpponentGamePlayer;
        local?: boolean;
        twoPlayer?: boolean;
        threePlayer?: boolean;
        compactOpponents?: number;
        active?: boolean;
        gameplayEnabled?: boolean;
        selectedSource?: SelectedSource | null;
        onselect?: (source: SelectedSource) => void;
        ondiscard?: (index: number) => void;
    };

    let { player, threePlayer = false, compactOpponents = 0, twoPlayer = false, local = false, active = false, gameplayEnabled = false, selectedSource = null, onselect, ondiscard }: Props = $props();
    let mobileLayout = $state(false);
    let discardDialog = $state<HTMLDialogElement>();
    let discardPileContent = $state<HTMLDivElement>();
    let inspectedDiscard = $state(0);
    const hand = $derived('hand' in player ? player.hand : []);
    const discardPiles = $derived('discardPiles' in player ? player.discardPiles : []);
    const discardTops = $derived('discardPiles' in player ? player.discardPiles.map((pile) => pile[0] ?? null) : player.discardTopCards);
    const inspectedCards = $derived(discardPiles[inspectedDiscard] ?? []);

    async function inspectDiscard(index: number) {
        const top = discardTops[index];
        if (!local || top === null || top === undefined || !discardDialog) return;
        inspectedDiscard = index;
        discardDialog.showModal();
        await tick();
        if (discardPileContent) discardPileContent.scrollTop = 0;
    }

    onMount(() => {
        const mobile = window.matchMedia('(max-width: 599px)');
        function updateLayout() {
            mobileLayout = mobile.matches;
        }
        updateLayout();
        mobile.addEventListener('change', updateLayout);
        return () => mobile.removeEventListener('change', updateLayout);
    });
</script>

<section data-player-id={player.playerId} class="player-panel" class:local class:active class:two-player={twoPlayer} class:three-player={threePlayer} class:compact-opponent={compactOpponents > 0} class:three-opponents={compactOpponents === 4} class:four-opponents={compactOpponents === 5} class:five-opponents={compactOpponents === 6} aria-label={`${player.username}'s ${local ? 'local' : 'opponent'} area${active ? ', current turn' : ''}`}>
    <header class="player-header">
        <img class="player-avatar" src={avatars[player.avatar - 1] ?? avatars[0]} alt={`${player.username}'s avatar`}>
        <h2>{#if player.playerNumber !== undefined}<span class="player-order" aria-label={`Player ${player.playerNumber}`}>{player.playerNumber}</span>{/if}<span class="opponent-name">{player.username}</span><span class="player-tag">#{player.playerId.substring(0, 3)}</span></h2>
        {#if local && active}
            <span class="player-badge" role="status">★ YOUR TURN</span>
        {:else if !local && active}
            <span class="player-badge">{compactOpponents ? 'TURN' : '★'}</span>
        {/if}
    </header>

    {#if !local}
        <p class="player-counts" aria-label={`${player.stockCount} stock cards, ${('handCount' in player ? player.handCount : hand.length)} hidden hand cards`}>
            <span>{twoPlayer && !compactOpponents ? 'Stock: ' : 'S:'}{player.stockCount}</span>
            <span>{twoPlayer && !compactOpponents ? 'Hand: ' : 'H:'}{('handCount' in player ? player.handCount : hand.length)}</span>
        </p>
    {/if}

    <div class="stock-and-discards">
        <div class="stock-pile">
            <h3>STOCK</h3>
            <div class="stock-stack">
                {#if !compactOpponents && (!threePlayer || local)}<div class="stock-back" aria-hidden="true"><GameCard back /></div>{/if}
                <GameCard value={cardValue(player.stockTopCard)} clickable={local && player.stockTopCard !== null} disabled={!gameplayEnabled} selected={selectedSource?.type === 'STOCK'} label={`${local ? 'Select your' : player.username + "'s"} stock top, ${cardValue(player.stockTopCard) ?? 'empty'}`} onclick={() => onselect?.({ type: 'STOCK' })} />
            </div>
            {#if local}
                <p class="stock-count">{player.stockCount} LEFT</p>
            {/if}
        </div>

        <div class="discards">
            <h3>DISCARDS</h3>
            <div class="discard-cards">
                {#each discardTops as card, index (index)}
                    {@const value = cardValue(card)}
                    {#if local}
                        <div class="discard-stack" class:has-cards-underneath={twoPlayer && mobileLayout && (discardPiles[index]?.length ?? 0) > 1}>
                            <GameCard {value} clickable={value !== null || selectedSource?.type === 'HAND'} disabled={!gameplayEnabled} selected={selectedSource?.type === 'DISCARD' && selectedSource.discardPileIndex === index} label={`Your discard ${index + 1}, ${value ?? 'empty'}`} onclick={() => selectedSource?.type === 'HAND' ? ondiscard?.(index) : onselect?.({ type: 'DISCARD', discardPileIndex: index })} />
                            {#if value !== null}
                                <button class="inspect-discard" type="button" aria-label={`Inspect discard pile ${index + 1}`} onclick={() => inspectDiscard(index)}>▤</button>
                            {/if}
                        </div>
                    {:else}
                        <GameCard {value} label={`${player.username}'s discard ${index + 1}, ${value ?? 'empty'}`} />
                    {/if}
                {/each}
            </div>
        </div>
    </div>

    {#if twoPlayer && !local}
        <div class="opponent-hand">
            <h3>HAND</h3>
            <div class="opponent-hand-cards">
                {#each Array(('handCount' in player ? player.handCount : hand.length)) as _, index (index)}
                    <GameCard back label={`${player.username}'s hand card ${index + 1}, face down`} />
                {/each}
            </div>
        </div>
    {/if}

    {#if local}
        <div class="hand">
            <h3>YOUR HAND <span>{hand.length}</span></h3>
            <div class="hand-cards">
                {#each hand as card, index (card.id)}
                    <GameCard value={cardValue(card)} clickable disabled={!gameplayEnabled} selected={selectedSource?.type === 'HAND' && selectedSource.cardId === card.id} label={`Select hand card ${index + 1}, ${cardValue(card)}`} onclick={() => onselect?.({ type: 'HAND', cardId: card.id })} />
                {/each}
            </div>
        </div>
        {#if !twoPlayer}
        <p class="selection-status" role="status">{selectedSource ? 'CARD SELECTED!' : 'TAP A CARD TO SELECT'}</p>
        {/if}
    {/if}
</section>

{#if local}
    <dialog class="discard-dialog" aria-label={`${player.username}'s discard pile ${inspectedDiscard + 1}`} bind:this={discardDialog}>
        <header class="discard-dialog-header">
            <h2>DISCARD PILE {inspectedDiscard + 1}</h2>
            <button type="button" aria-label="Close discard pile" onclick={() => discardDialog?.close()}>✕</button>
        </header>
        <div class="discard-contents" bind:this={discardPileContent}>
            <p>TOP</p>
            {#each inspectedCards as card (card.id)}
                <div class="inspected-card"><GameCard value={cardValue(card)} /></div>
            {/each}
            <p>BOTTOM</p>
        </div>
    </dialog>
{/if}

<style>
    .player-order {
        flex-shrink: 0;
        margin-right: 3px;
        padding: 0 2px;
        background: var(--comic-paper-white);
        border: 1px solid var(--comic-ink);
        border-radius: 2px;
        font-size: 0.65em;
        font-weight: 900;
    }

    .player-panel {
        min-width: 0;
        padding: 6px;
        background: var(--comic-paper-white);
        border: 3px solid var(--comic-ink);
        box-shadow: 3px 3px 0 var(--comic-ink);
    }

    .player-header {
        display: flex;
        align-items: center;
        gap: 4px;
        min-width: 0;
        margin-bottom: 5px;
    }

    .player-avatar {
        flex-shrink: 0;
        width: 23px;
        height: 23px;
        object-fit: cover;
        border: 2px solid var(--comic-ink);
        border-radius: 4px;
    }

    h2 {
        min-width: 0;
        overflow-wrap: anywhere;
        font-size: 11px;
        font-weight: 900;
    }

    .player-header h2 {
        display: flex;
        align-items: baseline;
        white-space: nowrap;
    }

    .opponent-name {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .player-tag {
        flex-shrink: 0;
        color: var(--comic-ink-soft);
        font-size: 0.8em;
        font-weight: 600;
    }

    .player-badge {
        flex-shrink: 0;
        margin-left: auto;
        font-size: 10px;
        font-weight: 900;
    }

    .player-counts {
        display: flex;
        gap: 8px;
        margin-bottom: 5px;
        font-size: 10px;
        font-weight: 900;
    }

    .stock-and-discards {
        display: grid;
        grid-template-columns: var(--opponent-stock-width, 26px) minmax(0, 1fr);
        gap: 7px;
    }

    h3 {
        margin-bottom: 5px;
        font-size: 8px;
        font-weight: 900;
        font-style: italic;
    }

    .stock-stack {
        position: relative;
        margin-left: 3px;
    }

    .stock-back {
        position: absolute;
        inset: 0;
        transform: translate(-3px, -2px) rotate(-4deg);
    }

    .discard-cards {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 3px;
        max-width: 220px;
    }

    .active {
        background: #B8FF3A;
        outline: 3px solid var(--comic-ink);
        outline-offset: 0;
        box-shadow: 3px 3px 0 var(--comic-ink);
    }

    .active .player-badge {
        color: var(--comic-ink);
    }

    .local {
        padding: 10px;
        background: var(--comic-cream);
        box-shadow: 4px 4px 0 var(--comic-ink);
    }

    .local.active {
        background: #B8FF3A;
        box-shadow: 5px 5px 0 var(--comic-ink);
    }

    .local .player-header {
        margin-bottom: 10px;
    }

    .local .player-avatar {
        width: 30px;
        height: 30px;
    }

    .local h2 {
        font-size: 17px;
        font-style: italic;
    }

    .local .player-badge {
        padding: 3px 7px;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
    }

    .local.active .player-badge {
        background: #B8FF3A;
    }

    .local .stock-and-discards {
        grid-template-columns: 52px minmax(0, 1fr);
        gap: 16px;
    }

    .local h3 {
        margin-bottom: 7px;
        font-size: 11px;
    }

    .local .discard-cards {
        gap: 8px;
        max-width: none;
        padding-top: 2px;
    }

    .stock-count {
        margin-top: 5px;
        font-size: 10px;
        font-weight: 900;
        text-align: center;
    }

    .hand {
        margin-top: 9px;
    }

    .hand h3 span {
        color: var(--comic-ink-soft);
        font-size: 10px;
    }

    .hand-cards {
        display: grid;
        grid-template-columns: repeat(5, minmax(0, 1fr));
        gap: 9px;
        padding: 3px 1px 0;
    }

    .selection-status {
        margin-top: 8px;
        font-size: 9px;
        font-weight: 900;
        font-style: italic;
        text-align: center;
    }

    @media (min-width: 600px) {
        .player-panel {
            padding: 10px;
        }

        h2 {
            font-size: 14px;
        }

        .player-avatar {
            width: 30px;
            height: 30px;
        }

        .player-counts,
        h3 {
            font-size: 11px;
        }

        .local {
            padding: 16px;
        }

        .local .stock-and-discards {
            grid-template-columns: 65px minmax(0, 1fr);
            gap: 25px;
            max-width: 440px;
            margin: 0 auto;
        }

        .hand {
            max-width: 440px;
            margin: 15px auto 0;
        }
    }

    @media (min-width: 1024px) {
        .local {
            display: grid;
            grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
            align-items: start;
            gap: 12px 20px;
        }

        .local .player-header,
        .selection-status {
            grid-column: 1 / -1;
            margin: 0;
        }

        .local .stock-and-discards,
        .hand {
            width: 100%;
            max-width: none;
            margin: 0;
        }

        .local h2 {
            font-size: 20px;
        }
    }

    @media (min-width: 1440px) {
        .local .stock-and-discards {
            grid-template-columns: 78px minmax(0, 1fr);
        }
    }

    .two-player {
        padding: 10px;
    }

    .two-player .player-avatar {
        width: 30px;
        height: 30px;
    }

    .two-player h2 {
        font-size: 16px;
    }

    .two-player .player-counts {
        font-size: 12px;
        margin-bottom: 8px;
    }

    .two-player h3 {
        font-size: 11px;
    }

    .two-player .stock-and-discards {
        grid-template-columns: 44px minmax(0, 1fr);
        gap: 14px;
    }

    .two-player .discard-cards {
        max-width: 228px;
        gap: 7px;
    }

    .opponent-hand {
        margin-top: 8px;
    }

    .opponent-hand-cards {
        display: flex;
        flex-wrap: wrap;
        gap: 5px;
    }

    .opponent-hand-cards :global(.game-card) {
        width: 30px;
    }

    .two-player.local .player-badge {
        color: var(--comic-ink-soft);
        background: var(--comic-paper-white);
        font-size: 11px;
    }

    .two-player.local.active {
        background: #B8FF3A;
        outline: 3px solid var(--comic-ink);
    }

    .two-player.local.active .player-badge {
        color: var(--comic-ink);
        background: #B8FF3A;
    }

    .two-player.local .hand {
        max-width: 350px;
    }

    .two-player.local .hand-cards {
        gap: 8px;
    }

    @media (max-width: 599px) {
        .two-player {
            padding: 8px;
        }

        .two-player:not(.local) {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            grid-template-rows: auto auto auto;
            align-content: start;
            column-gap: 6px;
            min-height: 175px;
        }

        .two-player:not(.local) .player-header {
            grid-column: 1;
            margin-bottom: 3px;
        }

        .two-player:not(.local) .player-counts {
            grid-column: 1;
            margin-bottom: 0;
        }

        .two-player:not(.local) .stock-and-discards {
            grid-column: 1 / -1;
            margin-top: 8px;
            grid-template-columns: 48px minmax(0, 1fr);
        }

        .two-player:not(.local) .discard-cards {
            width: 100%;
            max-width: 189px;
        }

        .two-player .opponent-hand {
            grid-column: 2;
            grid-row: 1 / 3;
            align-self: center;
            min-width: 0;
            margin-top: 0;
        }

        .two-player .opponent-hand h3 {
            display: none;
        }

        .two-player .opponent-hand-cards {
            justify-content: flex-end;
            max-width: 152px;
            gap: 3px;
        }

        .two-player .opponent-hand-cards :global(.game-card) {
            width: 28px;
        }

        .two-player h3,
        .two-player.local h3 {
            margin-bottom: 4px;
        }

        .two-player.local .player-header {
            margin-bottom: 6px;
        }

        .two-player.local .stock-and-discards {
            grid-template-columns: 48px minmax(0, 1fr);
            gap: 14px;
        }

        .two-player.local .discard-cards {
            max-width: 213px;
            gap: 7px;
        }

        .two-player.local .stock-count {
            margin-top: 4px;
        }

        .two-player.local .hand {
            max-width: 294px;
            margin: 6px auto 0;
        }

        .two-player.local .player-badge {
            padding: 3px 5px;
        }
    }
    @media (max-width: 599px) {
        .two-player.compact-opponent:not(.local) {
            display: flex;
            flex-direction: column;
            gap: 4px;
            min-height: 158px;
            min-width: 0;
            padding: 6px;
        }

        .compact-opponent .player-header {
            margin-bottom: 0;
            gap: 4px;
        }

        .compact-opponent .player-avatar {
            width: 23px;
            height: 23px;
        }

        .compact-opponent h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .compact-opponent .player-tag {
            flex-shrink: 0;
        }

        .compact-opponent .player-badge {
            font-size: 8px;
        }

        .two-player.compact-opponent .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.compact-opponent:not(.local) .stock-and-discards {
            grid-template-columns: 32px minmax(0, 1fr);
            align-items: start;
            gap: 7px;
            margin-top: 0;
        }

        .compact-opponent .stock-stack {
            margin-left: 0;
        }

        .compact-opponent h3 {
            font-size: 8px;
            margin-bottom: 3px;
        }

        .two-player.compact-opponent:not(.local) .discard-cards {
            gap: 3px;
            max-width: none;
        }

        .two-player.compact-opponent .opponent-hand {
            align-self: stretch;
            margin-top: auto;
        }

        .two-player.compact-opponent .opponent-hand-cards {
            justify-content: flex-start;
            max-width: none;
            gap: 3px;
        }

        .two-player.compact-opponent .opponent-hand-cards :global(.game-card) {
            width: 17px;
        }

        .two-player.three-opponents:not(.local) {
            min-height: 163px;
            padding: 5px;
            gap: 3px;
        }

        .three-opponents .player-header {
            display: grid;
            grid-template-columns: 20px minmax(0, 1fr);
        }

        .three-opponents .player-header h2 {
            font-size: 10px;
        }

        .three-opponents .player-header .player-badge {
            grid-column: 2;
            margin-left: 0;
        }

        .three-opponents .player-avatar {
            width: 20px;
            height: 20px;
        }

        .two-player.three-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 25px minmax(0, 1fr);
            gap: 4px;
        }

        .two-player.three-opponents:not(.local) .discard-cards {
            grid-template-columns: repeat(2, minmax(0, 25px));
        }

        .three-opponents .discards h3 {
            font-size: 7px;
        }

        .two-player.three-opponents .opponent-hand-cards {
            gap: 2px;
        }

        .two-player.three-opponents .opponent-hand-cards :global(.game-card) {
            width: 14px;
        }
    }

    .discard-stack {
        position: relative;
        min-width: 0;
        isolation: isolate;
    }

    .inspect-discard {
        position: absolute;
        right: -3px;
        bottom: -12px;
        z-index: 1;
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        padding: 0;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        border-radius: 4px;
        box-shadow: 1px 1px 0 var(--comic-ink);
        font-size: 17px;
        cursor: pointer;
        touch-action: manipulation;
    }

    .inspect-discard:focus-visible {
        outline: 3px solid var(--comic-purple);
        outline-offset: 2px;
    }

    @media (min-width: 1024px) {
        .two-player {
            --desktop-scale: 1.08;
            padding: 11px;
        }

        .two-player .player-avatar {
            width: calc(30px * var(--desktop-scale));
            height: calc(30px * var(--desktop-scale));
        }

        .two-player:not(.local) {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            column-gap: 18px;
        }

        .two-player:not(.local) .player-header,
        .two-player:not(.local) .player-counts,
        .two-player:not(.local) .stock-and-discards {
            grid-column: 1;
        }

        .two-player:not(.local) .opponent-hand {
            grid-column: 2;
            grid-row: 1 / 4;
            align-self: center;
            max-width: 210px;
            margin-top: 0;
        }

        .two-player:not(.local) .stock-back {
            display: none;
        }

        .two-player h2 {
            font-size: calc(16px * var(--desktop-scale));
        }

        .two-player.local h2 {
            font-size: calc(20px * var(--desktop-scale));
        }

        .two-player .player-counts {
            font-size: calc(12px * var(--desktop-scale));
        }

        .two-player h3,
        .two-player.local .player-badge {
            font-size: calc(11px * var(--desktop-scale));
        }

        .two-player .stock-and-discards {
            grid-template-columns: calc(44px * var(--desktop-scale)) minmax(0, 1fr);
            gap: 16px;
        }

        .two-player .discard-cards {
            max-width: calc(228px * var(--desktop-scale));
            gap: 8px;
        }

        .two-player .opponent-hand-cards :global(.game-card) {
            width: calc(30px * var(--desktop-scale));
        }

        .two-player.local .stock-and-discards {
            grid-template-columns: calc(65px * var(--desktop-scale)) minmax(0, 1fr);
            gap: 28px;
        }

        .two-player.local .discard-cards {
            max-width: calc(280px * var(--desktop-scale));
        }

        .two-player.local .hand {
            width: 100%;
            max-width: calc(490px * var(--desktop-scale));
        }

        .two-player.local .hand-cards {
            gap: 9px;
        }

        .two-player .stock-count {
            font-size: calc(10px * var(--desktop-scale));
        }

        .inspect-discard {
            width: 24px;
            height: 24px;
            bottom: -10px;
            font-size: 15px;
        }
    }

    @media (min-width: 1440px) {
        .two-player {
            --desktop-scale: 1.14;
            padding: 12px;
        }

        .two-player.local .stock-and-discards {
            grid-template-columns: calc(78px * var(--desktop-scale)) minmax(0, 1fr);
        }
    }

    .has-cards-underneath::before,
    .has-cards-underneath::after {
        content: '';
        position: absolute;
        inset: 0;
        z-index: -1;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        border-radius: 5px;
    }

    .has-cards-underneath::before {
        inset: -3px 3px 3px -3px;
    }

    .has-cards-underneath::after {
        inset: -1px 1px 1px -1px;
    }

    .discard-dialog {
        width: calc(100% - 32px);
        max-width: 260px;
        max-height: calc(var(--game-viewport-height, 100dvh) - 32px - env(safe-area-inset-top) - env(safe-area-inset-bottom));
        margin: auto;
        padding: 0;
        overflow: hidden;
        background: var(--comic-cream);
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 4px 4px 0 var(--comic-ink);
    }

    .discard-dialog[open] {
        display: flex;
        flex-direction: column;
    }

    .discard-dialog::backdrop {
        background: rgba(16, 25, 31, 0.6);
    }

    .discard-dialog-header {
        display: flex;
        flex-shrink: 0;
        align-items: center;
        justify-content: space-between;
        gap: 6px;
        padding: 6px 8px;
        background: var(--comic-yellow);
        border-bottom: 3px solid var(--comic-ink);
    }

    .discard-dialog-header h2 {
        font-size: 14px;
    }

    .discard-dialog-header button {
        width: 36px;
        height: 36px;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        font-weight: 900;
        cursor: pointer;
    }

    .discard-dialog-header button:focus-visible {
        outline: 3px solid var(--comic-purple);
        outline-offset: 2px;
    }

    .discard-contents {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 6px;
        min-height: 0;
        max-height: 380px;
        padding: 10px;
        overflow-y: auto;
        overflow-x: hidden;
        overscroll-behavior: contain;
    }

    .discard-contents p {
        font-size: 11px;
        font-weight: 900;
    }

    .inspected-card {
        width: 48px;
        flex-shrink: 0;
    }
    @media (min-width: 600px) {
        .three-player:not(.local) {
            display: flex;
            flex-direction: column;
            gap: 5px;
            padding: 10px;
        }

        .three-player:not(.local) .player-header {
            margin-bottom: 0;
        }

        .three-player:not(.local) h2 {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 14px;
        }

        .three-player:not(.local) .player-avatar {
            width: 28px;
            height: 28px;
        }

        .three-player:not(.local) .player-counts {
            margin-bottom: 0;
            font-size: 11px;
        }

        .three-player:not(.local) .stock-and-discards {
            grid-template-columns: 40px minmax(0, 1fr);
            gap: 12px;
        }

        .three-player:not(.local) .discard-cards {
            max-width: 190px;
            gap: 6px;
        }

        .three-player:not(.local) h3 {
            font-size: 10px;
            margin-bottom: 4px;
        }

        .three-player:not(.local) .opponent-hand {
            align-self: stretch;
            max-width: none;
            margin-top: auto;
            padding-top: 3px;
        }

        .three-player:not(.local) .opponent-hand h3 {
            display: none;
        }

        .three-player:not(.local) .opponent-hand-cards :global(.game-card) {
            width: 22px;
        }

        .three-player.local .discard-cards {
            max-width: 260px;
        }

        .three-player.local .hand {
            max-width: 490px;
        }
    }

    @media (min-width: 1440px) {
        .three-player:not(.local) {
            padding: 12px;
            gap: 6px;
        }

        .three-player:not(.local) .player-avatar {
            width: 32px;
            height: 32px;
        }

        .three-player:not(.local) h2 {
            font-size: 16px;
        }

        .three-player:not(.local) .stock-and-discards {
            grid-template-columns: 44px minmax(0, 1fr);
        }

        .three-player:not(.local) .discard-cards {
            max-width: 210px;
        }

        .three-player:not(.local) .opponent-hand-cards :global(.game-card) {
            width: 24px;
        }

        .three-player.local .discard-cards {
            max-width: 300px;
        }

        .three-player.local .hand {
            max-width: 550px;
        }
    }
    @media (min-width: 1024px) {
        .three-player:not(.local) {
            display: grid;
            grid-template-columns: minmax(0, 1fr) 80px;
            grid-template-rows: auto auto;
            align-content: start;
            column-gap: 10px;
            row-gap: 8px;
        }

        .three-player:not(.local) .player-header {
            grid-column: 1;
            grid-row: 1;
            min-width: 0;
        }

        .three-player:not(.local) .player-header h2 {
            min-width: 0;
        }

        .three-player:not(.local) .player-counts {
            grid-column: 2;
            grid-row: 1;
            flex-direction: column;
            align-items: flex-end;
            gap: 2px;
            font-size: 10px;
            white-space: nowrap;
        }

        .three-player:not(.local) .stock-and-discards {
            grid-column: 1;
            grid-row: 2;
            grid-template-columns: 34px minmax(0, 1fr);
            gap: 8px;
        }

        .three-player:not(.local) .stock-stack {
            margin-left: 0;
        }

        .three-player:not(.local) .discard-cards {
            max-width: 178px;
            gap: 4px;
        }

        .three-player:not(.local) .opponent-hand {
            grid-column: 2;
            grid-row: 2;
            align-self: start;
            min-width: 0;
            margin: 0;
            padding: 0;
        }

        .three-player:not(.local) .opponent-hand h3 {
            display: block;
        }

        .three-player:not(.local) .opponent-hand-cards {
            gap: 3px;
        }

        .three-player:not(.local) .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }

        .three-player.local .discard-cards {
            max-width: calc(280px * var(--desktop-scale));
        }

        .three-player.local .hand {
            max-width: calc(490px * var(--desktop-scale));
        }
    }

    @media (min-width: 1440px) {
        .three-player:not(.local) {
            grid-template-columns: minmax(0, 1fr) 126px;
            column-gap: 12px;
            row-gap: 10px;
        }

        .three-player:not(.local) .player-counts {
            flex-direction: row;
            justify-content: flex-end;
            align-items: center;
            gap: 8px;
            font-size: 11px;
        }

        .three-player:not(.local) .stock-and-discards {
            grid-template-columns: 44px minmax(0, 1fr);
            gap: 12px;
        }

        .three-player:not(.local) .discard-cards {
            max-width: 210px;
            gap: 6px;
        }

        .three-player:not(.local) .opponent-hand-cards {
            gap: 4px;
        }

        .three-player:not(.local) .opponent-hand-cards :global(.game-card) {
            width: 22px;
        }
    }
    @media (max-width: 1023px) {
        .two-player.three-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            min-width: 0;
            min-height: 166px;
            padding: 6px;
            gap: 4px;
        }

        .three-opponents .player-header {
            display: grid;
            grid-template-columns: 28px minmax(0, 1fr);
            gap: 4px;
            margin-bottom: 0;
        }

        .three-opponents .player-avatar {
            width: 28px;
            height: 28px;
        }

        .three-opponents .player-header h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .three-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .three-opponents .player-tag {
            flex-shrink: 0;
        }

        .three-opponents .player-header .player-badge {
            grid-column: 2;
            margin-left: 0;
            font-size: 8px;
        }

        .two-player.three-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.three-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 30px minmax(0, 1fr);
            align-items: start;
            gap: 6px;
            margin-top: 0;
        }

        .three-opponents .stock-stack {
            margin-left: 0;
        }

        .three-opponents h3,
        .three-opponents .discards h3 {
            font-size: 8px;
            margin-bottom: 3px;
        }

        .two-player.three-opponents:not(.local) .discard-cards {
            grid-template-columns: repeat(4, minmax(0, 1fr));
            max-width: none;
            gap: 3px;
        }

        .two-player.three-opponents .opponent-hand {
            align-self: stretch;
            margin-top: auto;
        }

        .three-opponents .opponent-hand h3 {
            display: none;
        }

        .two-player.three-opponents .opponent-hand-cards {
            justify-content: flex-start;
            max-width: none;
            gap: 3px;
        }

        .two-player.three-opponents .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }
    }
    @media (min-width: 1024px) {
        .two-player.three-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding: 8px;
        }

        .three-opponents .player-header {
            gap: 4px;
            margin-bottom: 0;
        }

        .three-opponents .player-avatar {
            width: 32px;
            height: 32px;
        }

        .three-opponents .player-header h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 12px;
        }

        .three-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .three-opponents .player-tag {
            flex-shrink: 0;
        }

        .three-opponents .player-badge {
            font-size: 9px;
        }

        .two-player.three-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.three-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 32px minmax(0, 1fr);
            gap: 6px;
        }

        .three-opponents .stock-stack {
            margin-left: 0;
        }

        .three-opponents h3 {
            margin-bottom: 3px;
            font-size: 9px;
        }

        .two-player.three-opponents:not(.local) .discard-cards {
            max-width: 156px;
            gap: 4px;
        }

        .two-player.three-opponents .opponent-hand {
            display: flex;
            align-items: center;
            align-self: stretch;
            gap: 6px;
            max-width: none;
            min-width: 0;
            margin-top: auto;
            padding-top: 4px;
        }

        .three-opponents .opponent-hand h3 {
            flex-shrink: 0;
            margin-bottom: 0;
        }

        .three-opponents .opponent-hand-cards {
            min-width: 0;
            gap: 3px;
        }

        .two-player.three-opponents .opponent-hand-cards :global(.game-card) {
            width: 20px;
        }
    }

    @media (min-width: 1200px) {
        .two-player.three-opponents:not(.local) {
            padding: 10px;
            gap: 5px;
        }

        .three-opponents .player-avatar {
            width: 34px;
            height: 34px;
        }

        .three-opponents .player-header h2 {
            font-size: 13px;
        }

        .two-player.three-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 36px minmax(0, 1fr);
            gap: 8px;
        }

        .two-player.three-opponents:not(.local) .discard-cards {
            max-width: 168px;
        }

        .two-player.three-opponents .opponent-hand-cards :global(.game-card) {
            width: 22px;
        }
    }

    @media (min-width: 1440px) {
        .two-player.three-opponents:not(.local) {
            padding: 12px;
        }

        .three-opponents .player-avatar {
            width: 36px;
            height: 36px;
        }

        .three-opponents .player-header h2 {
            font-size: 15px;
        }

        .two-player.three-opponents .player-counts {
            font-size: 12px;
        }

        .two-player.three-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 44px minmax(0, 1fr);
            gap: 10px;
        }

        .two-player.three-opponents:not(.local) .discard-cards {
            max-width: 190px;
            gap: 5px;
        }

        .two-player.three-opponents .opponent-hand-cards :global(.game-card) {
            width: 24px;
        }
    }
    @media (max-width: 1023px) {
        .two-player.four-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            min-width: 0;
            min-height: 166px;
            padding: 6px;
            gap: 4px;
        }

        .four-opponents .player-header {
            display: grid;
            grid-template-columns: 28px minmax(0, 1fr);
            gap: 4px;
            margin-bottom: 0;
        }

        .four-opponents .player-avatar {
            width: 28px;
            height: 28px;
        }

        .four-opponents .player-header h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .four-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .four-opponents .player-tag {
            flex-shrink: 0;
        }

        .four-opponents .player-header .player-badge {
            grid-column: 2;
            margin-left: 0;
            font-size: 8px;
        }

        .two-player.four-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.four-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 30px minmax(0, 1fr);
            align-items: start;
            gap: 6px;
            margin-top: 0;
        }

        .four-opponents .stock-stack {
            margin-left: 0;
        }

        .four-opponents h3,
        .four-opponents .discards h3 {
            font-size: 8px;
            margin-bottom: 3px;
        }

        .two-player.four-opponents:not(.local) .discard-cards {
            grid-template-columns: repeat(4, minmax(0, 1fr));
            max-width: none;
            gap: 3px;
        }

        .two-player.four-opponents .opponent-hand {
            align-self: stretch;
            margin-top: auto;
        }

        .four-opponents .opponent-hand h3 {
            display: none;
        }

        .two-player.four-opponents .opponent-hand-cards {
            justify-content: flex-start;
            max-width: none;
            gap: 3px;
        }

        .two-player.four-opponents .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }
    }
    @media (min-width: 1024px) {
        .two-player.four-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding: 6px;
        }

        .four-opponents .player-header {
            gap: 4px;
            margin-bottom: 0;
        }

        .four-opponents .player-avatar {
            width: 30px;
            height: 30px;
        }

        .four-opponents .player-header h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .four-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .four-opponents .player-tag {
            flex-shrink: 0;
        }

        .four-opponents .player-badge {
            font-size: 9px;
        }

        .two-player.four-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.four-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 30px minmax(0, 1fr);
            gap: 4px;
        }

        .four-opponents .stock-stack {
            margin-left: 0;
        }

        .four-opponents h3 {
            margin-bottom: 3px;
            font-size: 9px;
        }

        .two-player.four-opponents:not(.local) .discard-cards {
            max-width: 130px;
            gap: 3px;
        }

        .two-player.four-opponents .opponent-hand {
            display: flex;
            align-items: center;
            align-self: stretch;
            gap: 4px;
            max-width: none;
            min-width: 0;
            margin-top: auto;
            padding-top: 4px;
        }

        .four-opponents .opponent-hand h3 {
            flex-shrink: 0;
            margin-bottom: 0;
        }

        .four-opponents .opponent-hand-cards {
            min-width: 0;
            gap: 3px;
        }

        .two-player.four-opponents .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }
    }

    @media (min-width: 1200px) {
        .two-player.four-opponents:not(.local) {
            padding: 8px;
            gap: 5px;
        }

        .four-opponents .player-avatar {
            width: 32px;
            height: 32px;
        }

        .four-opponents .player-header h2 {
            font-size: 12px;
        }

        .two-player.four-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 34px minmax(0, 1fr);
            gap: 8px;
        }

        .two-player.four-opponents:not(.local) .discard-cards {
            max-width: 140px;
        }

        .two-player.four-opponents .opponent-hand-cards :global(.game-card) {
            width: 20px;
        }
    }

    @media (min-width: 1440px) {
        .two-player.four-opponents:not(.local) {
            padding: 10px;
        }

        .four-opponents .player-avatar {
            width: 38px;
            height: 38px;
        }

        .four-opponents .player-header h2 {
            font-size: 14px;
        }

        .two-player.four-opponents .player-counts {
            font-size: 11px;
        }

        .two-player.four-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 40px minmax(0, 1fr);
            gap: 8px;
        }

        .two-player.four-opponents:not(.local) .discard-cards {
            max-width: 160px;
            gap: 5px;
        }

        .two-player.four-opponents .opponent-hand-cards :global(.game-card) {
            width: 24px;
        }
    }
    @media (max-width: 1023px) {
        .two-player.five-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            min-width: 0;
            min-height: 166px;
            padding: 6px;
            gap: 4px;
        }

        .five-opponents .player-header {
            display: grid;
            grid-template-columns: 28px minmax(0, 1fr);
            gap: 4px;
            margin-bottom: 0;
        }

        .five-opponents .player-avatar {
            width: 28px;
            height: 28px;
        }

        .five-opponents .player-header h2 {
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .five-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .five-opponents .player-tag {
            flex-shrink: 0;
        }

        .five-opponents .player-header .player-badge {
            grid-column: 2;
            margin-left: 0;
            font-size: 8px;
        }

        .two-player.five-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.five-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 30px minmax(0, 1fr);
            align-items: start;
            gap: 6px;
            margin-top: 0;
        }

        .five-opponents .stock-stack {
            margin-left: 0;
        }

        .five-opponents h3,
        .five-opponents .discards h3 {
            font-size: 8px;
            margin-bottom: 3px;
        }

        .two-player.five-opponents:not(.local) .discard-cards {
            grid-template-columns: repeat(4, minmax(0, 1fr));
            max-width: none;
            gap: 3px;
        }

        .two-player.five-opponents .opponent-hand {
            align-self: stretch;
            margin-top: auto;
        }

        .five-opponents .opponent-hand h3 {
            display: none;
        }

        .two-player.five-opponents .opponent-hand-cards {
            justify-content: flex-start;
            max-width: none;
            gap: 3px;
        }

        .two-player.five-opponents .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }
    }
    @media (min-width: 1024px) {
        .two-player.five-opponents:not(.local) {
            display: flex;
            flex-direction: column;
            gap: 4px;
            padding: 4px;
        }

        .five-opponents .player-header {
            display: grid;
            grid-template-columns: 28px minmax(0, 1fr);
            gap: 3px;
            margin-bottom: 0;
        }

        .five-opponents .player-avatar {
            width: 28px;
            height: 28px;
        }

        .five-opponents .player-header h2 {
            grid-column: 2;
            display: flex;
            min-width: 0;
            overflow: hidden;
            white-space: nowrap;
            font-size: 11px;
        }

        .five-opponents .opponent-name {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .five-opponents .player-tag {
            flex-shrink: 0;
        }

        .five-opponents .player-badge {
            grid-column: 2;
            margin-left: 0;
            font-size: 8px;
        }

        .two-player.five-opponents .player-counts {
            margin-bottom: 0;
            font-size: 10px;
        }

        .two-player.five-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 26px minmax(0, 1fr);
            gap: 4px;
        }

        .five-opponents .stock-stack {
            margin-left: 0;
        }

        .five-opponents h3 {
            margin-bottom: 3px;
            font-size: 9px;
        }

        .two-player.five-opponents:not(.local) .discard-cards {
            max-width: 104px;
            gap: 2px;
        }

        .two-player.five-opponents .opponent-hand {
            display: flex;
            align-items: center;
            align-self: stretch;
            gap: 4px;
            max-width: none;
            min-width: 0;
            margin-top: auto;
            padding-top: 4px;
        }

        .five-opponents .opponent-hand h3 {
            flex-shrink: 0;
            margin-bottom: 0;
        }

        .five-opponents .opponent-hand-cards {
            min-width: 0;
            gap: 2px;
        }

        .two-player.five-opponents .opponent-hand-cards :global(.game-card) {
            width: 16px;
        }
    }

    @media (min-width: 1200px) {
        .two-player.five-opponents:not(.local) {
            padding: 6px;
            gap: 5px;
        }

        .five-opponents .player-header {
            grid-template-columns: 32px minmax(0, 1fr);
        }

        .five-opponents .player-avatar {
            width: 32px;
            height: 32px;
        }

        .five-opponents .player-header h2 {
            font-size: 12px;
        }

        .two-player.five-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 30px minmax(0, 1fr);
            gap: 6px;
        }

        .two-player.five-opponents:not(.local) .discard-cards {
            max-width: 120px;
        }

        .two-player.five-opponents .opponent-hand-cards :global(.game-card) {
            width: 18px;
        }
    }

    @media (min-width: 1440px) {
        .two-player.five-opponents:not(.local) {
            padding: 8px;
        }

        .five-opponents .player-header {
            grid-template-columns: 36px minmax(0, 1fr);
        }

        .five-opponents .player-avatar {
            width: 36px;
            height: 36px;
        }

        .five-opponents .player-header h2 {
            font-size: 14px;
        }

        .two-player.five-opponents .player-counts {
            font-size: 11px;
        }

        .two-player.five-opponents:not(.local) .stock-and-discards {
            grid-template-columns: 34px minmax(0, 1fr);
            gap: 6px;
        }

        .two-player.five-opponents:not(.local) .discard-cards {
            max-width: 136px;
            gap: 3px;
        }

        .two-player.five-opponents .opponent-hand-cards :global(.game-card) {
            width: 22px;
        }
    }
</style>
