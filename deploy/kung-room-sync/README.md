# Kung Server

One small Node server (no dependencies, no Docker) for:

- **Live run sync**: Kung clients on the same Hypixel server share the revealed map, room
  states, secrets and deaths. Memory only, expires after `KUNG_LIVE_ROOM_SYNC_TTL_MS`.
- **Hypixel proxy** for the CA50/C50 calculator. The Hypixel key stays on the server, never in
  a jar. Each player is cached for `KUNG_PROFILE_CACHE_MS`, each client IP gets
  `KUNG_PROFILE_LIMIT_PER_5_MIN` lookups.
- Optional shared room data, stored in `KUNG_ROOM_DATA_PATH`.

## Endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /health` | `{"ok": true}`, no auth |
| `POST /auth/challenge` | new sign-in challenge, no auth |
| `POST /auth/login` | trade a joined challenge for a session, no auth |
| `GET /runs/live?runKey=…` | live run snapshot from the other clients |
| `POST /runs/live/report` | report this client's live run state |
| `GET /hypixel/profiles?uuid=…` | the player's SkyBlock profiles, cut down to what Kung reads (503 without `HYPIXEL_API_KEY`) |
| `GET /rooms` | shared room data |
| `POST /rooms/report` | add a learned room |

Every other endpoint needs `Authorization: Bearer <session token>`.

## Auth

No shared token and nothing to type. Kung signs in the way vanilla joins a server:

1. `POST /auth/challenge` returns a random challenge, valid for 60 s and single use.
2. The client calls Mojang's `joinServer` with that challenge as server id. Its access token
   only goes to Mojang, never to this server.
3. `POST /auth/login` with `{"name", "challenge"}`. The server asks Mojang's `hasJoined` who
   joined and returns a session token, valid for `KUNG_SESSION_TTL_MS` (default 12 h).

Live run reports and snapshots use the session's player name, so nobody can post as someone
else. Sessions live in memory; a restart just makes clients sign in again.
`KUNG_AUTH_LIMIT_PER_5_MIN` limits sign-in attempts per client IP.

`KUNG_ALLOWED_UUIDS` is the allowlist: comma-separated Minecraft UUIDs, dashes optional.
Anyone else gets 403. Empty lets in any verified Minecraft account. Add a friend by appending
their UUID and restarting the service.

## Setup on a Debian/Ubuntu LXC

Proxmox: an unprivileged container with `nesting=1` is enough. As root, from this folder:

```bash
# Node.js LTS from NodeSource
apt-get install -y ca-certificates curl gnupg
install -d -m 755 /etc/apt/keyrings
curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_24.x nodistro main" > /etc/apt/sources.list.d/nodesource.list
apt-get update && apt-get install -y nodejs

# Server, config (root, mode 600) and systemd unit
install -D -m 644 kung-room-sync-server.mjs /opt/kung-server/kung-room-sync-server.mjs
install -m 600 .env.example /etc/kung-server.env
nano /etc/kung-server.env            # KUNG_ALLOWED_UUIDS, optional HYPIXEL_API_KEY
cp kung-server.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now kung-server
```

The unit runs as a throwaway system user; room data lives in `/var/lib/kung-server/`.

## Health check

```bash
curl http://127.0.0.1:8765/health    # {"ok": true}
journalctl -u kung-server -n 20
```

## Updating

Copy the new `kung-room-sync-server.mjs` to `/opt/kung-server/`, then:

```bash
systemctl restart kung-server
```

From the Proxmox host: `pct push <ctid> kung-room-sync-server.mjs /opt/kung-server/kung-room-sync-server.mjs --perms 644`.

Tests (fake Mojang and Hypixel, no network): `node --test "deploy/kung-room-sync/*.test.mjs"`.

## Kung client setup

Nothing to type: `https://kung.bengcoding.org` is the built-in server URL. Live map sync is
still opt-in (`/kung roomsync enable`). The CA50 calculator uses the server when you have no
own Hypixel key. A player not on the allowlist is refused once and not retried until the game
restarts. `/kung roomsync server <url>` overrides the URL; a blank URL means no server.

## Cloudflare Tunnel

The tunnel is the only way in; no router port is opened.

1. Install `cloudflared` from Cloudflare's apt repo (pkg.cloudflare.com).
2. Create a tunnel in Zero Trust, then `cloudflared service install <tunnel-token>`.
3. Public hostname `kung.bengcoding.org` → `http://localhost:8765`.
4. Bind the server to localhost with the drop-in `host.conf`, so the env file stays untouched.
   A plain `Environment=` line would lose to the env file's `KUNG_ROOM_SYNC_HOST=0.0.0.0`
   (`EnvironmentFile=` wins in systemd), so the drop-in sets it on the command line:

   ```bash
   mkdir -p /etc/systemd/system/kung-server.service.d
   cat > /etc/systemd/system/kung-server.service.d/host.conf <<'EOF'
   [Service]
   ExecStart=
   ExecStart=/usr/bin/env KUNG_ROOM_SYNC_HOST=127.0.0.1 /usr/bin/node /opt/kung-server/kung-room-sync-server.mjs
   EOF
   systemctl daemon-reload && systemctl restart kung-server
   ```
