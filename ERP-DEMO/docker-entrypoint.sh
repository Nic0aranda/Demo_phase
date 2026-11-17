#!/usr/bin/env bash
# docker-entrypoint.sh, ERP-DEMO
#
# Patches Index.html so the frontend's config.ts picks up the Spring Boot
# API base before main.js loads (config.ts reads window.ERP_API_BASE).
# Then delegates to the project's own node static server (server.js).
#
# Usage: the ERP-DEMO Dockerfile sets this as ENTRYPOINT and passes
#        -e ERP_API_BASE=... . The default (uncommented) value is used only
#        when the container is served from the host "localhost" (dev flow).
set -euo pipefail

API_BASE="${ERP_API_BASE:-http://localhost:8080/api/v1}"

if [[ "${API_BASE}" != "http://localhost:8080/api/v1" ]]; then
  # Inject a single <script> before </head>. The value is JSON-safe (a URL),
  # so quoting is enough; no shell metacharacters are expected here.
  sed -i "s|</head>|<script>window.ERP_API_BASE = '${API_BASE}';</script></head>|" \
    /app/Index.html
fi

exec node server.js
