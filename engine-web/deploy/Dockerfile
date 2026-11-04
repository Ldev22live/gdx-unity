# Web game runtime image (engine-web, TD-036): builds the optimized JavaScript player and serves it with nginx.
# Build from the repository root so Gradle sees every module:
#   docker build -f engine-web/deploy/Dockerfile -t gdxunity-web .
#   docker run -d --name gdxunity-web -p 8080:80 --restart unless-stopped gdxunity-web

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# Gradle's quiet logging hides progress; lifecycle shows which task failed if the build breaks
RUN chmod +x gradlew && ./gradlew --no-daemon -Dorg.gradle.logging.level=lifecycle \
    :engine-web:gdx_teavm_web_js_release_build

FROM nginx:1.27-alpine
COPY engine-web/deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/engine-web/build/dist/js/release/webapp /usr/share/nginx/html
# WEB-INF is gdx-teavm's Jetty dev-server descriptor; nothing a browser needs
RUN rm -rf /usr/share/nginx/html/WEB-INF
EXPOSE 80
