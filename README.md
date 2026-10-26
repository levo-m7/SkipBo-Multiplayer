
## How to Run

Clone the repository:

```bash
git clone https://github.com/levo-m7/SkipBo-Multiplayer.git
cd SkipBo-Multiplayer
```

### Option 1 - Java 21

```bash
java -jar skipbo-multiplayer.jar
```

### Option 2 - Docker

```bash
docker compose up -d --build
```

### Option 3 - Cloudflare Quick Tunnel (Testing)

Start SkipBo with a temporary public URL:

```bash
docker compose -f compose.tunnel.yml up -d --build
```

Get your public Cloudflare URL:

```bash
docker compose -f compose.tunnel.yml logs tunnel
```

Share the generated `https://...trycloudflare.com` URL with your friends.

The link is temporary and may change when the tunnel restarts.

For local play, open http://localhost:7070.

