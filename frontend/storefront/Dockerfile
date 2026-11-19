# Builds the Storefront and serves it with nginx on port 8080. Build context: frontend/storefront.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
# A template, which the image's entrypoint fills in from the environment as it starts (nginx.conf).
# Only these two are replaced, so no other variable can stand in for one of nginx's own.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    NGINX_ENVSUBST_FILTER="^(API_GATEWAY|NGINX_LOCAL_RESOLVERS)$" \
    API_GATEWAY=api-gateway:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
