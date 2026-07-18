FROM mcr.microsoft.com/playwright:v1.61.1-noble
WORKDIR /app
COPY douyin-worker/package.json douyin-worker/package-lock.json ./
RUN npm ci --omit=dev
COPY douyin-worker/src ./src
ENV NODE_ENV=production
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
EXPOSE 8787
CMD ["node", "/app/src/server.js"]
