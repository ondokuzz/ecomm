# Builds the Admin Console and serves it with nginx on port 8090. Build context: frontend/admin-console.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
# A template, which the image's entrypoint fills in from the environment as it starts (nginx.conf).
# Only these three are replaced, so no other variable can stand in for one of nginx's own.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    NGINX_ENVSUBST_FILTER="^(API_GATEWAY|OIDC_AUTHORITY|NGINX_LOCAL_RESOLVERS)$" \
    API_GATEWAY=api-gateway:8080 \
    OIDC_AUTHORITY=http://localhost:8180/realms/ecomm
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8090
