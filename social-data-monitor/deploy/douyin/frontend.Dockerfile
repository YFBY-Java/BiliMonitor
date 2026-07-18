FROM node:20-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_API_BASE_URL=
ARG VITE_DOUYIN_ENABLED=true
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DOUYIN_ENABLED=${VITE_DOUYIN_ENABLED}
RUN npm run build

FROM nginx:alpine
COPY deploy/douyin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
