FROM node:22-alpine

WORKDIR /app

COPY kung-room-sync-server.mjs /app/kung-room-sync-server.mjs

RUN mkdir -p /data \
    && addgroup -S kung \
    && adduser -S kung -G kung \
    && chown -R kung:kung /app /data

USER kung

ENV PORT=8765
ENV KUNG_ROOM_DATA_PATH=/data/known-rooms.json
ENV KUNG_LIVE_ROOM_SYNC_TTL_MS=180000

EXPOSE 8765

CMD ["node", "/app/kung-room-sync-server.mjs"]
