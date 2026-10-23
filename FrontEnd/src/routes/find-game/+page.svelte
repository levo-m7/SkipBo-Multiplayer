<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import {
		globalRoomsStore,
		connectToGlobalRooms,
		disconnectFromGlobalRooms
	} from '#lib/state/globalRooms.svelte.ts';

	let searchInput = $state('');
	let joining = $state(false);
	let joinError = $state<string | null>(null);
	let searchField: HTMLInputElement;

	const filteredRooms = $derived(
		globalRoomsStore.rooms.filter(
			(room) =>
				room.state === 'WAITING' &&
				room.full === false &&
				(room.playerTarget === 1 || room.playerCount < room.playerTarget) &&
				room.roomId.substring(0, 8).toLowerCase().startsWith(searchInput.trim().toLowerCase())
		)
	);

	async function joinRoom(roomId: string) {
		if (joining) return;
		joining = true;
		joinError = null;
		try {
			await goto(`/waiting-lobby?roomId=${encodeURIComponent(roomId)}`);
		} catch {
			joinError = 'COULD NOT JOIN ROOM';
		} finally {
			joining = false;
		}
	}

	function goBack() {
		void goto('/');
	}

	onMount(() => {
		connectToGlobalRooms();
		return disconnectFromGlobalRooms;
	});
</script>

<svelte:head>
	<title>Find Game</title>
</svelte:head>

<main class="game-page">
	<h1 class="page-title find-title">FIND GAME</h1>

	<section class="search-panel">
		<label for="game-search">ROOM CODE</label>
		<input
			id="game-search"
			type="text"
			maxlength="8"
			placeholder="Enter room code..."
			bind:value={searchInput}
			bind:this={searchField}
		/>
		<button class="main-action find-action" type="button" onclick={() => searchField.focus()}>
			FIND
		</button>
	</section>

	<h2 class="lobby-heading">WAITING LOBBIES</h2>

	{#if globalRoomsStore.error}
		<p role="alert">{globalRoomsStore.error}</p>
	{/if}
	{#if joinError}
		<p role="alert">{joinError}</p>
	{/if}

	<section class="lobby-list">
		{#each filteredRooms as room (room.roomId)}
			<div class="lobby" id={room.roomId}>
				<div>
					<p class="room-code">ID: <strong>{room.roomId.substring(0, 8)}</strong></p>
					<h3>{room.roomName}</h3>
					<p>{room.playerCount} / {room.playerTarget === 1 ? 'ANY' : room.playerTarget} PLAYERS</p>
				</div>
				<button class="join-button" type="button" disabled={joining} onclick={() => joinRoom(room.roomId)}>
					JOIN
				</button>
			</div>
		{/each}
	</section>
	<button class="join-button back-button" type="button" onclick={goBack}>BACK</button>
</main>

<style>
	.game-page {
		width: 90%;
		max-width: 430px;
		min-height: 100svh;

		margin: 0 auto;
		padding: 22px 0;
	}
	.page-title {
		width: fit-content;

		margin: 0 auto 18px;
		padding: 10px 20px;

		border: 3px solid var(--comic-ink);
		box-shadow: var(--comic-shadow);

		font-size: 28px;
		font-weight: 900;
		font-style: italic;
	}

	.find-title {
		background: var(--comic-paper-white);
	}
	.search-panel {
		padding: 16px;

		background: var(--comic-cream);

		border: var(--comic-border);
		box-shadow: var(--comic-shadow);
	}
	.search-panel label {
		display: block;

		margin-bottom: 8px;

		font-size: 16px;
		font-weight: 900;
		font-style: italic;
	}
	.search-panel input {
		width: 100%;
		height: 46px;

		margin-bottom: 16px;
		padding: 0 12px;

		background: var(--comic-paper-white);

		border: 2px solid var(--comic-ink);

		font-size: 18px;
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

	.find-action {
		background: var(--comic-lightgreen);
	}
	.lobby-heading {
		margin: 20px 0 10px;

		font-size: 18px;
		font-weight: 900;
		font-style: italic;
	}
	.back-button {
		width: 100%;
		margin-top: 10px;
	}

	.lobby-list {
		max-height: 45svh;
		overflow-y: auto;
		overflow-x: hidden;
		overscroll-behavior-y: contain;
		padding: 2px 6px 6px 0;
		display: flex;
		flex-direction: column;

		gap: 10px;
	}
	.lobby {
		flex-shrink: 0;
		gap: 12px;
		display: flex;
		align-items: center;
		justify-content: space-between;

		padding: 12px;

		background: var(--comic-paper-white);

		border: 3px solid var(--comic-ink);
		box-shadow: 4px 4px 0 var(--comic-ink);
	}
	.lobby > div {
		min-width: 0;
		overflow-wrap: anywhere;
	}

	.lobby .room-code {
		margin: 0 0 4px;
		font-size: 15px;
		font-weight: 900;
	}

	.lobby h3 {
		font-size: 17px;
		font-weight: 900;
		font-style: italic;
	}
	.lobby p {
		margin-top: 4px;

		font-size: 14px;
		font-weight: 700;
	}
	.join-button {
		flex-shrink: 0;
		min-height: 42px;

		padding: 0 14px;

		background: var(--comic-lightgreen);

		border: 3px solid var(--comic-ink);

		font-weight: 900;
		font-style: italic;
	}

	@media (hover: hover) and (pointer: fine) {
		.lobby:hover {
			transform: translateY(-2px);
			box-shadow: 3px 3px 0 var(--comic-ink);
		}
	}

	@media (min-width: 600px) {
		.game-page {
			width: 82%;
			max-width: 620px;

			padding: 30px 0;
		}
		.page-title {
			margin-bottom: 22px;
			padding: 12px 26px;

			font-size: 32px;
		}
		.search-panel {
			padding: 20px;
		}
		.search-panel label {
			font-size: 18px;
		}
		.search-panel input {
			height: 50px;

			font-size: 19px;
		}

		.main-action {
			min-height: 62px;

			font-size: 25px;
		}
		.lobby {
			padding: 14px;
		}
	}

	@media (min-width: 1024px) {
		.game-page {
			width: 70%;
			max-width: 720px;

			padding: 40px 0;
		}
		.page-title {
			padding: 14px 30px;

			font-size: 36px;
		}
		.search-panel {
			padding: 24px;
		}
		.search-panel label {
			font-size: 19px;
		}
		.search-panel input {
			height: 54px;

			font-size: 20px;
		}

		.main-action {
			min-height: 68px;

			font-size: 28px;
		}
		.lobby-heading {
			font-size: 21px;
		}
		.lobby h3 {
			font-size: 19px;
		}
		.lobby p {
			font-size: 15px;
		}
		.join-button {
			min-height: 46px;

			padding: 0 18px;

			font-size: 16px;
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
		.search-panel {
			padding: 28px;
		}
		.search-panel input {
			height: 58px;

			font-size: 21px;
		}

		.main-action {
			min-height: 72px;

			font-size: 30px;
		}
		.lobby {
			padding: 16px;
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
		.search-panel {
			padding: 32px;
		}
		.search-panel label {
			font-size: 21px;
		}
		.search-panel input {
			height: 62px;

			font-size: 22px;
		}

		.main-action {
			min-height: 76px;

			font-size: 32px;
		}
		.lobby-heading {
			font-size: 24px;
		}
		.lobby h3 {
			font-size: 21px;
		}
		.lobby p {
			font-size: 16px;
		}
		.join-button {
			min-height: 50px;

			font-size: 17px;
		}
	}

	@media (min-width: 2560px) {
		.game-page {
			max-width: 980px;
		}
		.page-title {
			font-size: 48px;
		}
		.search-panel {
			padding: 36px;
		}
		.main-action {
			min-height: 80px;

			font-size: 34px;
		}
	}
</style>
