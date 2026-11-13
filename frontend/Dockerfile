FROM node:22-alpine AS build
ARG VITE_CVCS_MODE=server
ENV VITE_CVCS_MODE=$VITE_CVCS_MODE
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/ /usr/share/nginx/html/
EXPOSE 80
