
# SkipBo Multiplayer

![SkipBo Multiplayer Gameplay](pics/Picture3.png)

## Description

A small, simple, in-memory SkipBo multiplayer game you can self-host and play with your friends in your browser.

Play with 2–6 players, create or join rooms, and chat in real time. No accounts or database required.

## Tech Stack

- **Backend:** Java 21, Javalin, WebSockets
- **Frontend:** SvelteKit, TypeScript
- **Deployment:** Executable JAR or Docker Compose

## How to Run

Clone the repository:

```bash
git clone https://github.com/levo-m7/SkipBo-Multiplayer.git
cd SkipBo-Multiplayer
```

**Option 1 — Java 21**

```bash
java -jar skipbo-multiplayer.jar
```

**Option 2 — Docker**

```bash
docker compose up -d --build
```

Open **http://localhost:7070** in your browser.

## Screenshots

### Homepage

![SkipBo Homepage](pics/Picture1.png)

### Waiting Lobby

![SkipBo Waiting Lobby](pics/Picture2.png)

### Mobile Gameplay

<img src="pics/Picture4.png" alt="SkipBo gameplay on iPhone" width="320">

## Notes

- All rooms, sessions, and game state are stored in memory and reset when the server restarts.
- For LAN play, open `http://HOST_IP:7070` on other devices. Firewall or WSL port forwarding may be necessary.
