# syntax=docker/dockerfile:1.7
# ============================================================
# GraphVisual — Multi-stage Dockerfile
# Builds the Java/JUNG graph visualization project and packages
# it as a runnable fat JAR with all runtime dependencies.
# ============================================================

# ---- Stage 1: Build ----
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy project files
COPY Gvisual/ ./Gvisual/

# Download JUnit for test compilation (cached as a separate layer)
RUN mkdir -p Gvisual/lib/test \
    && curl -fsSL -o Gvisual/lib/test/junit-4.13.2.jar \
       https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar \
    && curl -fsSL -o Gvisual/lib/test/hamcrest-core-1.3.jar \
       https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

# Compile production sources.
#
# A handful of *Test.java files have historically been checked in under
# Gvisual/src/ alongside production code; production javac must NOT see
# those (no JUnit on the production classpath). The canonical test root
# is Gvisual/test/.
#
# Target JDK 11 bytecode — the codebase uses `var` (Java 10+) and stream
# `.toList()` (Java 16+) in a few places, but compiles cleanly under
# --release 11 elsewhere. We use 17 to match CI's upper matrix entry.
RUN mkdir -p Gvisual/build/classes \
    && find Gvisual/src -name '*.java' ! -name '*Test.java' > Gvisual/sources.txt \
    && javac -encoding UTF-8 --release 17 \
       -cp "$(find Gvisual/lib -name '*.jar' -not -path '*/test/*' | tr '\n' ':')" \
       -d Gvisual/build/classes \
       @Gvisual/sources.txt

# Compile tests
RUN mkdir -p Gvisual/build/test/classes \
    && find Gvisual/test -name '*Test.java' > Gvisual/test-sources.txt \
    && javac -encoding UTF-8 --release 17 \
       -cp "Gvisual/build/classes:$(find Gvisual/lib -name '*.jar' | tr '\n' ':')" \
       -d Gvisual/build/test/classes \
       @Gvisual/test-sources.txt

# Run a fast, deterministic subset of tests to verify the build.
# Full test suite runs in CI; the Docker build only needs a smoke barrier.
RUN java -cp "Gvisual/build/classes:Gvisual/build/test/classes:$(find Gvisual/lib -name '*.jar' | tr '\n' ':')" \
    org.junit.runner.JUnitCore \
    app.UtilMethodsTest \
    gvisual.EdgeTest \
    gvisual.GraphStatsTest \
    gvisual.ShortestPathFinderTest \
    gvisual.CommunityDetectorTest

# Create fat JAR with all dependencies merged.
# Excludes signed-JAR signatures (would invalidate after merge) and source/javadoc jars.
RUN mkdir -p Gvisual/dist \
    && cd Gvisual/build/classes \
    && for jar in $(find /app/Gvisual/lib -name '*.jar' \
       -not -path '*/test/*' \
       -not -name '*-javadoc.jar' \
       -not -name '*-sources.jar'); do \
         jar xf "$jar" 2>/dev/null || true; \
       done \
    && rm -rf META-INF/MANIFEST.MF META-INF/*.SF META-INF/*.DSA META-INF/*.RSA 2>/dev/null || true \
    && jar cfe /app/Gvisual/dist/Gvisual.jar gvisual.Main .

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="GraphVisual" \
      org.opencontainers.image.description="Interactive JUNG-based social network graph visualization and analysis tool" \
      org.opencontainers.image.source="https://github.com/sauravbhattacharya001/GraphVisual" \
      org.opencontainers.image.licenses="MIT"

# X11 libraries for Swing GUI support (optional X11 forwarding from host)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libx11-6 libxext6 libxrender1 libxtst6 libxi6 libfreetype6 fontconfig \
    && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN groupadd --gid 1001 graphvisual \
    && useradd --uid 1001 --gid graphvisual --no-log-init --create-home graphvisual

WORKDIR /app

# Copy built JAR and data files
COPY --from=builder --chown=graphvisual:graphvisual /app/Gvisual/dist/Gvisual.jar ./Gvisual.jar
COPY --chown=graphvisual:graphvisual Gvisual/graph.txt ./graph.txt
COPY --chown=graphvisual:graphvisual Gvisual/images/ ./images/

USER graphvisual

# Health check: verify the JAR is loadable. The GUI itself needs $DISPLAY,
# so we just sanity-check that `jar tf` can read the archive.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD jar tf Gvisual.jar > /dev/null 2>&1 || exit 1

# Default: run the GUI application.
# For X11 forwarding from host: docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix <image>
ENTRYPOINT ["java"]
CMD ["-jar", "Gvisual.jar"]
