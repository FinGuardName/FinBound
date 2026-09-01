FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY --chown=node:node server.mjs ./server.mjs

USER node
EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=2s --start-period=2s --retries=12 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:8080/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"]

CMD ["node", "server.mjs"]
