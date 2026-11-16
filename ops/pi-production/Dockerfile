FROM nginx@sha256:901e593c95dc85f5a729f5765516321366ef4ac401821431d408897e95287d0a
# The pinned upstream image predates fixes in Alpine's package repository.
# Scan the resulting image on every build; the release records its immutable ID.
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/nginx.conf
USER 101:101
ENTRYPOINT ["nginx", "-g", "daemon off;"]
