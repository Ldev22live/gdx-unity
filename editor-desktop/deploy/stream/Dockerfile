# Editor streaming image (TD-037): the desktop editor on a virtual display (Xvfb, software OpenGL), shown in a
# browser through VNC and noVNC. One container is one user's editor session.
# Build from the repository root so Gradle sees every module:
#   docker build -f editor-desktop/deploy/stream/Dockerfile -t gdxunity-editor-stream .
# Check the image (starts the display, renders frames in the editor, exits 0/1):
#   docker run --rm --read-only --tmpfs /tmp:rw,exec,nosuid,size=1g -v gdxunity-smoke:/data gdxunity-editor-stream smoke
# Run sessions through editor-session.sh, which sets the limits and the network; see docs/editor-streaming.md.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# Gradle's quiet logging hides progress; lifecycle shows which task failed if the build breaks.
# On Linux this collects the editor jars with the Linux natives, plus the sample project, in build/package/input.
RUN chmod +x gradlew && ./gradlew --no-daemon -Dorg.gradle.logging.level=lifecycle \
    :editor-desktop:prepareEditorPackageInput

# The full JDK, not a JRE: editor scripts are compiled in-process with jdk.compiler (TD-019).
FROM eclipse-temurin:21-jdk
ENV DEBIAN_FRONTEND=noninteractive
# xvfb: virtual display. openbox: window manager (the editor window needs one to maximize).
# x11vnc, novnc, websockify: the display as a web page. libgl1/mesa: software OpenGL (llvmpipe).
# libX*: libraries GLFW loads at run time. zenity: backs the native file dialogs (tinyfd) inside the display.
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc novnc websockify openbox zenity \
        libgl1 libgl1-mesa-dri libglx-mesa0 \
        libxrandr2 libxinerama1 libxcursor1 libxi6 libxxf86vm1 libxrender1 libxtst6 \
        fontconfig fonts-dejavu-core curl tini ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf vnc.html /usr/share/novnc/index.html \
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin editor \
    && mkdir /data && chown 10001:10001 /data

COPY --from=build /src/editor-desktop/build/package/input/ /opt/gdxunity/app/
COPY editor-desktop/deploy/stream/entrypoint.sh /usr/local/bin/gdxunity-entrypoint
RUN chmod 755 /usr/local/bin/gdxunity-entrypoint

# Projects, editor preferences and logs live in /data (a volume per user); everything else can be read-only
USER 10001:10001
VOLUME /data
EXPOSE 6080
# "Ready" means the noVNC page is served; the editor window appears a few seconds later
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
    CMD curl -fs http://127.0.0.1:6080/vnc.html > /dev/null || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/gdxunity-entrypoint"]
CMD ["session"]
