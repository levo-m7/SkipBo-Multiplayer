<script lang="ts">
    import { cards, wildCard, cardBack, type CardValue } from '#lib/data/cards.ts';

    type Props = {
        value?: CardValue | null;
        back?: boolean;
        wildValue?: number;
        selected?: boolean;
        clickable?: boolean;
        disabled?: boolean;
        label?: string;
        onclick?: () => void;
    };

    let { value = null, back = false, wildValue, selected = false, clickable = false, disabled = false, label, onclick }: Props = $props();

    const image = $derived(back ? cardBack : value === 'WILD' ? wildCard : value === null ? null : cards[value - 1]);
    const cardLabel = $derived(label ?? (back ? 'Face-down card' : value === null ? 'Empty pile' : value === 'WILD' ? 'Skip-Bo wild card' : `Card ${value}`));
</script>

{#snippet cardFace()}
    {#if image}
        <img src={image} alt="" draggable="false" width="100" height="150">
    {:else}
        <span class="empty-card" aria-hidden="true">+</span>
    {/if}
    {#if !back && value === 'WILD' && wildValue !== undefined}
        <strong class="wild-value" aria-label={`Wild represents ${wildValue}`}>{wildValue}</strong>
    {/if}
{/snippet}

{#if clickable}
    <button class="game-card" class:selected class:empty={!image} type="button" aria-label={cardLabel} aria-pressed={selected} {disabled} {onclick}>
        {@render cardFace()}
    </button>
{:else}
    <div class="game-card" class:empty={!image} role="img" aria-label={cardLabel}>
        {@render cardFace()}
    </div>
{/if}

<style>
    .wild-value {
        position: absolute;
        right: 3px;
        bottom: 3px;
        display: grid;
        place-items: center;
        min-width: 18px;
        height: 18px;
        padding: 0 1px;
        background: var(--comic-yellow);
        color: var(--comic-ink);
        border: 2px solid var(--comic-ink);
        border-radius: 50%;
        box-shadow: 1px 1px 0 var(--comic-ink);
        font-size: 10px;
        font-weight: 900;
        line-height: 1;
        pointer-events: none;
    }

    @media (min-width: 1024px) {
        .wild-value {
            right: 4px;
            bottom: 4px;
            min-width: 22px;
            height: 22px;
            font-size: 12px;
        }
    }

    .game-card {
        position: relative;
        display: block;
        width: 100%;
        min-width: 0;
        aspect-ratio: 2 / 3;
        padding: 0;
        overflow: hidden;
        background: var(--comic-paper-white);
        border: 2px solid var(--comic-ink);
        border-radius: 5px;
        box-shadow: 2px 2px 0 var(--comic-ink);
    }

    .game-card img {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        object-fit: contain;
        pointer-events: none;
        user-select: none;
    }

    .empty {
        background: var(--comic-cream);
        border-style: dashed;
        box-shadow: none;
    }

    .empty-card {
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
        color: var(--comic-ink-soft);
        font-size: clamp(14px, 4vw, 28px);
        font-weight: 900;
    }

    button {
        cursor: pointer;
        touch-action: manipulation;
    }

    .selected {
        outline: 3px solid var(--comic-purple);
        outline-offset: 1px;
        transform: translateY(-4px);
        box-shadow: 3px 5px 0 var(--comic-ink);
    }

    button:focus-visible {
        outline: 3px solid var(--comic-purple);
        outline-offset: 3px;
    }

    button:disabled {
        cursor: default;
        opacity: 0.6;
    }

    @media (hover: hover) and (pointer: fine) {
        button:not(:disabled):hover {
            filter: brightness(1.06);
        }
    }
</style>
