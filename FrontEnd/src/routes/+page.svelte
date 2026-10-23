<script lang='ts'>
    import { avatars } from '#lib/data/avatars.ts';
    import { store } from '#lib/state/store.svelte.ts';
    import { goto } from '$app/navigation';
    import { updateUsername, updateAvatar } from '#lib/api/player.ts';
    
    function handleUsernameInput(event: Event) {
        const input = event.currentTarget as HTMLInputElement;
        
        const username = input.value
            .replace(/[^a-zA-Z0-9_-]/g, '')
            .slice(0, 8);
        input.value = username;
        store.player.username = username;
    }
    
    async function saveUsername() {
        if (store.player.username.length === 0) {
            store.player.username = 'user';
        }
        
        const player = await updateUsername(store.player.username);
        store.player = player;
    }
    
    async function selectAvatar(avatar: unknown) {
        let validAvatar = 1;
        
        if ( typeof avatar === 'number' && Number.isInteger(avatar) && avatar >= 1 && avatar <= 10) {
            validAvatar = avatar;
        }
        
        const player = await updateAvatar(validAvatar);
        
        store.player = player;
    }

    function hostGame()
    {
        goto("/host-game");
    }
    
</script>

<div id="home-container">
    <header id="home-header">
        <div id="home-logo-container">
            <h1 id="home-logo">SKIPBO</h1>
        </div>
        <div id="home-tagline-container">
            <h2 id="home-tagline">Some people have it, some people don't</h2>
        </div>
    </header>
    
    <main id="home-main">
        <div id="username-box">
            <label for="username-input">YOUR NAME</label>
            <input 
            id="username-input" 
            type="text"
            maxlength="8"
            bind:value={store.player.username}
            oninput={handleUsernameInput}
            onchange={saveUsername}
            >
            <span class="nickname-pencil">&#9998;</span>
        </div>
        <div id="avatar-box">
            <h3>CHOOSE YOUR AVATAR</h3>
            <div id="avatar-grid">
                {#each avatars as avatar, index}
                <button
                id={store.player.avatar === index + 1 ? 'avatar-selected' : undefined}
                class="avatar"
                type="button"
                onclick={() => selectAvatar(index + 1)}
                >
                <img
                src={avatar}
                alt={`Avatar ${index + 1}`}
                >
            </button>
            {/each}
        </div>
    </div>
</main>
<footer id="home-footer">
    <button 
        id="host-game-button" 
        class="home-button" 
        type="button"
        onclick={hostGame}
        >
            <span>HOST GAME</span>
    </button>
    <button id="find-game-button" class="home-button" type="button" onclick={() => goto('/find-game')}>
        <span>FIND GAME</span>
    </button>
</footer>
</div>


<style>
    #home-container {
        width: 100%;
        min-height: 100svh;
        
        display: flex;
        flex-direction: column;
        
        background:
        radial-gradient(
        circle,
        rgba(16, 25, 31, 0.18) 1px,
        transparent 1.5px
        ),
        var(--comic-blue);
        
        background-size: 7px 7px;
    }
    
    #home-header {
        position: relative;
        
        display: flex;
        flex-direction: column;
        align-items: center;
        
        gap: 12px;
        
        padding-top: 20px;
        padding-bottom: 30px;
        
        overflow: hidden;
    }
    
    #home-header::before {
        content: "";
        
        position: absolute;
        
        width: max(650px, 100vw);
        height: max(650px, 100vw);
        
        left: 50%;
        top: calc(110px - max(325px, 50vw));
        
        transform: translateX(-50%);
        
        background:
        repeating-conic-gradient(
        from 0deg,
        rgba(16, 25, 31, 0.16) 0deg 1deg,
        transparent 1deg 11deg
        );
        
        pointer-events: none;
    }
    
    #home-logo-container {
        position: relative;
        z-index: 1;
        
        width: 100%;
        max-width: 360px;
        
        margin: 0 auto;
    }
    
    #home-tagline-container {
        position: relative;
        z-index: 1;
        
        width: 100%;
        max-width: 350px;
        
        margin: 0 auto;
        
        padding: 0 10px;
    }
    
    #home-logo {
        margin: 0;
        
        font-size: clamp(68px, 21vw, 94px);
        line-height: 0.85;
        
        font-weight: 900;
        font-style: italic;
        
        letter-spacing: -7px;
        
        color: var(--comic-orange);
        
        -webkit-text-stroke: 4px var(--comic-ink);
        paint-order: stroke fill;
        
        text-shadow:
        7px 7px 0 var(--comic-ink),
        8px 8px 0 var(--comic-red);
        
        text-align: center;
        
        transform: rotate(-3deg);
    }
    
    #home-tagline {
        margin: 0;
        
        padding: 7px 18px;
        
        background: var(--comic-cream);
        color: var(--comic-ink);
        
        border: 3px solid var(--comic-ink);
        
        box-shadow:
        4px
        4px
        0
        var(--comic-ink);
        
        font-size: 15px;
        font-weight: 900;
        font-style: italic;
        
        text-align: center;
        
        transform: rotate(-2deg);
    }
    
    #home-main {
        width: 90%;
        max-width: 420px;
        
        margin: 0 auto;
        
        padding: 16px;
        
        background: var(--comic-cream);
        
        border: 3px solid var(--comic-ink);
        border-radius: 6px;
        
        box-shadow:
        4px
        4px
        0
        var(--comic-ink);
    }
    
    #username-box {
        position: relative;
        
        display: flex;
        flex-direction: column;
        
        gap: 8px;
        
        margin-bottom: 16px;
    }
    
    #username-box label {
        font-size: 16px;
        font-weight: 900;
        font-style: italic;
    }
    
    #username-input {
        width: 100%;
        height: 46px;
        
        padding:
        0
        46px
        0
        12px;
        
        background: var(--comic-paper-white);
        
        border: 2px solid var(--comic-ink);
        
        font-size: 18px;
    }
    
    .nickname-pencil {
        position: absolute;
        
        right: 12px;
        bottom: 10px;
        
        font-size: 24px;
        
        transform: rotate(-10deg);
        
        pointer-events: none;
    }
    
    #avatar-box h3 {
        margin:
        0
        0
        10px
        0;
        
        font-size: 16px;
        font-weight: 900;
        font-style: italic;
    }
    
    #avatar-grid {
        display: grid;
        
        grid-template-columns:
        repeat(5, 1fr);
        
        gap: 6px;
    }
    
    .avatar {
        padding: 0;
        
        aspect-ratio: 1;
        
        overflow: hidden;
        
        background: var(--comic-blue);
        
        border: 3px solid var(--comic-ink);
        
        border-radius: 4px;
    }
    
    .avatar img {
        width: 100%;
        height: 100%;
        
        object-fit: cover;
    }
    
    #avatar-selected {
        border: 5px solid var(--comic-purple);
        
        /* transform:
        translateY(-2px)
        rotate(-1deg); */
    }
    
    #home-footer {
        display: flex;
        flex-direction: column;
        
        gap: 12px;
        
        width: 90%;
        max-width: 420px;
        
        margin:
        18px
        auto
        24px;
    }
    
    #host-game-button,
    #find-game-button {
        width: 100%;
        min-height: 56px;
        
        padding: 0 18px;
        
        border: 3px solid var(--comic-ink);
        border-radius: 5px;
        
        box-shadow:
        5px
        5px
        0
        var(--comic-ink);
        
        font-size: 22px;
        line-height: 1;
        
        font-weight: 900;
        font-style: italic;
        
        letter-spacing: -1px;
        
        text-align: left;
    }
    
    #host-game-button {
        background: var(--comic-lightpurple);
        
        transform: rotate(-1deg);
    }
    
    #find-game-button {
        background: var(--comic-lightgreen);
        
        transform: rotate(0.7deg);
    }
    
    @media (min-width: 600px) {
        
        #home-header {
            gap: 16px;
            
            padding-top: 30px;
            padding-bottom: 40px;
        }
        
        #home-logo-container {
            max-width: 460px;
        }
        
        #home-tagline-container {
            max-width: 430px;
        }
        
        #home-logo {
            font-size: 105px;
            
            letter-spacing: -8px;
            
            -webkit-text-stroke: 5px var(--comic-ink);
            
            text-shadow:
            8px 8px 0 var(--comic-ink),
            10px 10px 0 var(--comic-red);
        }
        
        #home-tagline {
            padding: 9px 22px;
            
            font-size: 18px;
        }
        
        #home-main {
            width: 82%;
            max-width: 600px;
            
            padding: 20px;
        }
        
        #avatar-grid {
            gap: 10px;
        }
        
        #home-footer {
            width: 82%;
            max-width: 600px;
            
            gap: 14px;
        }
        
        #host-game-button,
        #find-game-button {
            min-height: 64px;
            
            padding: 0 22px;
            
            font-size: 25px;
        }
    }
    
    @media (min-width: 1024px) {
        
        #home-header {
            gap: 18px;
            
            padding-top: 40px;
            padding-bottom: 45px;
        }
        
        #home-logo-container {
            max-width: 540px;
        }
        
        #home-tagline-container {
            max-width: 500px;
        }
        
        #home-logo {
            font-size: 120px;
        }
        
        #home-tagline {
            padding: 11px 26px;
            
            font-size: 20px;
        }
        
        #home-main {
            width: 70%;
            max-width: 680px;
            
            padding: 24px;
        }
        
        #username-box label,
        #avatar-box h3 {
            font-size: 18px;
        }
        
        #username-input {
            height: 52px;
            
            font-size: 20px;
        }
        
        #avatar-grid {
            gap: 12px;
        }
        
        #home-footer {
            width: 70%;
            max-width: 680px;
            
            margin-top: 22px;
        }
        
        #host-game-button,
        #find-game-button {
            min-height: 68px;
            
            padding: 0 26px;
            
            font-size: 28px;
        }
    }
    
    @media (min-width: 1440px) {
        
        #home-header {
            padding-top: 45px;
            padding-bottom: 50px;
        }
        
        #home-logo-container {
            max-width: 620px;
        }
        
        #home-tagline-container {
            max-width: 560px;
        }
        
        #home-logo {
            font-size: 132px;
            
            -webkit-text-stroke: 6px var(--comic-ink);
            
            text-shadow:
            9px 9px 0 var(--comic-ink),
            12px 12px 0 var(--comic-red);
        }
        
        #home-tagline {
            font-size: 22px;
        }
        
        #home-main {
            max-width: 760px;
            
            padding: 28px;
        }
        
        #home-footer {
            max-width: 760px;
        }
        
        #host-game-button,
        #find-game-button {
            min-height: 72px;
            
            font-size: 30px;
        }
    }
    
    @media (min-width: 1920px) {
        
        #home-header {
            padding-top: 55px;
            padding-bottom: 55px;
        }
        
        #home-logo-container {
            max-width: 700px;
        }
        
        #home-tagline-container {
            max-width: 620px;
        }
        
        #home-logo {
            font-size: 145px;
        }
        
        #home-tagline {
            padding: 12px 30px;
            
            font-size: 24px;
        }
        
        #home-main {
            max-width: 820px;
            
            padding: 32px;
        }
        
        #home-footer {
            max-width: 820px;
        }
        
        #host-game-button,
        #find-game-button {
            min-height: 76px;
            
            font-size: 32px;
        }
    }
    
    @media (hover: hover) and (pointer: fine) {
        
        #home-container,
        #host-game-button,
        #find-game-button,
        .avatar {
            cursor: pointer;
        }
        
        #host-game-button,
        #find-game-button,
        
        .avatar {
            transition:
            transform 0.15s ease,
            filter 0.15s ease,
            box-shadow 0.15s ease;
        }
        
        .avatar:not(#avatar-selected):hover {
            transform:
            translateY(-3px)
            scale(1.08)
            rotate(-2deg);
            
            filter: brightness(1.1);
        }
        
        #host-game-button:hover {
            transform:
            translateY(-3px)
            scale(1.03)
            rotate(-1deg);
            
            filter: brightness(1.08);
            
            box-shadow:
            7px 7px 0 var(--comic-ink);
        }
        
        #find-game-button:hover {
            transform:
            translateY(-3px)
            scale(1.03)
            rotate(0.7deg);
            
            filter: brightness(1.08);
            
            box-shadow:
            7px 7px 0 var(--comic-ink);
        }
        
        .avatar:hover {
            transform:
            translateY(-3px)
            scale(1.08)
            rotate(-2deg);
            
            filter: brightness(1.1);
        }
    }
</style>