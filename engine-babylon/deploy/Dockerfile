# Forgeroom web player and editor: nginx serving the built site.
#
# Build from the root of the extracted package (the folder that contains site/ and deploy/):
#   docker build -f deploy/Dockerfile -t forgeroom-web .
#   docker run -d --name forgeroom-web --restart unless-stopped -p 8080:80 forgeroom-web
#
# The container speaks plain http; put a reverse proxy with HTTPS in front of it (the music studio needs HTTPS).
FROM nginx:1.27-alpine

COPY deploy/container-nginx.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html/

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q --spider http://127.0.0.1/index.html || exit 1
