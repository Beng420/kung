# Kung Room Sync Server

Small Node/Docker setup for the Kung room sync homeserver.

The important dungeon feature is temporary live run sync: Kung clients on the same Hypixel server exchange the currently revealed map, room clear/completion state, room secret progress, and per-player run secret/death counts. This data is memory-only and expires automatically.

The server also still supports optional shared learned room data for trusted data collection:

- `GET /rooms`
- `POST /rooms/report`
- `GET /runs/live`
- `POST /runs/live/report`

Learned room data is persisted in `./data/known-rooms.json`; live run sync is not.

## Start On Proxmox

Copy this folder to the server:

```text
deploy/kung-room-sync/
```

Then on the server:

```bash
cd deploy/kung-room-sync
cp .env.example .env
nano .env
docker compose up -d --build
```

Or run it directly with Node:

```bash
export KUNG_ROOM_SYNC_TOKEN="change-me"
export KUNG_ROOM_DATA_PATH="/root/kung/data/known-rooms.json"
node kung-room-sync-server.mjs
```

Use a long random `KUNG_ROOM_SYNC_TOKEN`. Everyone using the sync must enter the same server URL and token in Kung.

## Check Health

```bash
curl http://localhost:8765/health
```

Expected response:

```json
{
  "ok": true
}
```

## Kung Client Setup

In Minecraft, every Kung user who should share live maps needs:

```text
/kung room sync server http://your-server:8765
/kung room sync token your-token
/kung room sync enable
/kung room sync upload true
```

If the server is exposed publicly, put it behind a firewall, VPN, Tailscale, or reverse proxy with HTTPS. The bearer token is the only app-level auth this tiny server has.
