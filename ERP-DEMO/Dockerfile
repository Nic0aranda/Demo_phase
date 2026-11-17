# syntax=docker/dockerfile:1
#
# Demo_phase/ERP-DEMO, TypeScript ERP frontend (vanilla TS, no bundler).
#
# Builds with `tsc` (no bundler), then serves `dist/` + the project root
# (Index.html, server.js, CSS) with the project's own `server.js`.
#
# docker-entrypoint.sh patches Index.html so the frontend knows where the
# Spring Boot API lives. Override at runtime:
#   docker run --rm -p 5500:5500 \
#     -e ERP_API_BASE=http://<host-ip>:8080/api/v1 \
#     erp-demo:latest
#
# The default (no env) points at http://localhost:8080/api/v1 which is the
# localhost dev flow described in the project README.

FROM node:22-bookworm AS builder

WORKDIR /app

# package + lock first (fast rebuilds).
COPY Demo_phase/ERP-DEMO/package.json Demo_phase/ERP-DEMO/package-lock.json ./
RUN npm ci

# Source + static assets not part of the tsc output.
COPY Demo_phase/ERP-DEMO/src ./src
COPY Demo_phase/ERP-DEMO/views ./views
COPY Demo_phase/ERP-DEMO/CSS ./CSS
COPY Demo_phase/ERP-DEMO/server.js ./
COPY Demo_phase/ERP-DEMO/Index.html ./
COPY Demo_phase/ERP-DEMO/docker-entrypoint.sh ./
COPY Demo_phase/ERP-DEMO/tsconfig.json ./

RUN chmod +x ./docker-entrypoint.sh

RUN npm run build

FROM node:22-bookworm-slim AS erp-demo

WORKDIR /app

# Everything the static server serves: compiled JS, the HTML shell,
# the CSS and the project root assets.
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/server.js ./
COPY --from=builder /app/Index.html ./
COPY --from=builder /app/CSS ./CSS
COPY --from=builder /app/views ./views
COPY --from=builder /app/docker-entrypoint.sh ./

# The entrypoint injects the runtime API URL into Index.html before serving it.
RUN chown node:node /app /app/Index.html

# The API base is injected into Index.html before main.js runs (see
# docker-entrypoint.sh). Default = host's localhost Spring Boot API.
ENV PORT=5500
ENV ERP_API_BASE=http://localhost:8080/api/v1

EXPOSE 5500

# Runs as node (uid 1000) so the files are readable.
USER node

ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["node", "server.js"]
