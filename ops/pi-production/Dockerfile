FROM nginx@sha256:8eb2b5540eae779603cc3db84b3986a15ecb5308b54303146eee9837c137b51a
# The pinned upstream image predates fixes in Alpine's package repository.
# Scan the resulting image on every build; the release records its immutable ID.
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/nginx.conf
USER 101:101
ENTRYPOINT ["nginx", "-g", "daemon off;"]
