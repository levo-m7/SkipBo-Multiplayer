<script lang="ts">
	import favIcon  from '#lib/assets/SkipBo.svg';
	import '../app.css';
	
	import { onMount } from 'svelte';
	import { getPlayer } from '#lib/api/player.ts';
	import { store } from '#lib/state/store.svelte.ts';
	
	let { children } = $props();
	
	onMount(() => {
		initializePlayer();
	});
	
	async function initializePlayer() {
		try {
			const player = await getPlayer();
			console.log(player);
			store.player = player;
		} catch (error) {
			console.error('Could not initialize player:', error);
		}
	}
	
</script>

<svelte:head>
	<link rel="icon" type="image/png" href={ favIcon }/>
</svelte:head>

{@render children()}