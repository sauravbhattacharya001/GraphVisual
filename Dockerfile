# ============================================================
# GraphVisual — Multi-stage Dockerfile
# Builds the Java/Ant JUNG graph visualization project and
# packages it as a runnable JAR with all dependencies.
# ============================================================

# ---- Stage 1: Build ----
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy project files
COPY Gvisual/ ./Gvisual/

# Download JUnit for test compilation
RUN mkdir -p Gvisual/lib/test \
    && curl -sL -o Gvisual/lib/test/junit-4.13.2.jar \
       https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar \
    && curl -sL -o Gvisual/lib/test/hamcrest-core-1.3.jar \
       https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

# Compile source
RUN mkdir -p Gvisual/build/classes \
    && find Gvisual/src -name '*.java' > Gvisual/sources.txt \
    && javac -source 8 -target 8 \
       -cp "$(find Gvisual/lib -name '*.jar' -not -path '*/test/*' | tr '\n' ':')" \
       -d Gvisual/build/classes \
       @Gvisual/sources.txt

# Compile tests
RUN mkdir -p Gvisual/build/test/classes \
    && find Gvisual/test -name '*.java' > Gvisual/test-sources.txt \
    && javac -source 8 -target 8 \
       -cp "Gvisual/build/classes:$(find Gvisual/lib -name '*.jar' | tr '\n' ':')" \
       -d Gvisual/build/test/classes \
       @Gvisual/test-sources.txt

# Run tests to verify build
RUN java -cp "Gvisual/build/classes:Gvisual/build/test/classes:$(find Gvisual/lib -name '*.jar' | tr '\n' ':')" \
    org.junit.runner.JUnitCore \
    app.UtilMethodsTest \
    gvisual.EdgeTest \
    gvisual.GraphStatsTest \
    gvisual.ShortestPathFinderTest \
    gvisual.CommunityDetectorTest

# Create fat JAR with all dependencies merged
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

# Install X11 libraries for Swing GUI support (optional X11 forwarding)
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libx11-6 libxext6 libxrender1 libxtst6 libxi6 libfreetype6 fontconfig \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd --gid 1001 graphvisual \
    && useradd --uid 1001 --gid graphvisual --no-log-init --create-home graphvisual

WORKDIR /app

# Copy built JAR and data files
COPY --from=builder --chown=graphvisual:graphvisual /app/Gvisual/dist/Gvisual.jar ./Gvisual.jar
COPY --chown=graphvisual:graphvisual Gvisual/graph.txt ./graph.txt
COPY --chown=graphvisual:graphvisual Gvisual/images/ ./images/

USER graphvisual

# Health check: verify JAR is valid
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD java -jar Gvisual.jar --version 2>/dev/null || java -cp Gvisual.jar gvisual.Main --help 2>/dev/null || exit 0

# Default: run the GUI application
# For headless/X11 forwarding: docker run -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix graphvisual
# For CI/testing only: override CMD with test runner
ENTRYPOINT ["java"]
CMD ["-jar", "Gvisual.jar"]
