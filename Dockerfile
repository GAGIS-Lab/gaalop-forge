FROM maven:3.9.9-eclipse-temurin-17 AS backend-build

WORKDIR /workspace

COPY . .
RUN mvn -pl gaalop-rest -am -DskipTests package

FROM node:22-bookworm-slim AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN corepack enable \
    && corepack prepare pnpm@10.25.0 --activate \
    && pnpm install --frozen-lockfile

COPY frontend/ ./
COPY codegen-ganja/src/main/resources/de/gaalop/ganja/ganja.js \
    /workspace/codegen-ganja/src/main/resources/de/gaalop/ganja/ganja.js
RUN pnpm build

FROM ubuntu:22.04 AS maxima-build

ARG MAXIMA_VERSION=5.45.1

RUN apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends \
        build-essential ca-certificates curl python3 python-is-python3 sbcl texinfo \
    && rm -rf /var/lib/apt/lists/*

RUN curl --fail --location --retry 5 \
        "https://downloads.sourceforge.net/project/maxima/Maxima-source/${MAXIMA_VERSION}-source/maxima-${MAXIMA_VERSION}.tar.gz" \
        --output /tmp/maxima.tar.gz \
    && tar -xzf /tmp/maxima.tar.gz -C /tmp \
    && cd "/tmp/maxima-${MAXIMA_VERSION}" \
    && ./configure --prefix=/usr/local --enable-sbcl --disable-gcl \
    && make -j"$(nproc)" \
    && make install DESTDIR=/maxima-root

FROM eclipse-temurin:17-jre-jammy

LABEL org.opencontainers.image.title="GAALOP Forge" \
      org.opencontainers.image.description="Geometric algebra compilation, optimization, code generation, and visualization platform" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.licenses="LGPL-3.0-or-later"

RUN apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends ca-certificates curl nginx sbcl \
    && groupadd --system gaalop \
    && useradd --system --gid gaalop --home-dir /app --shell /usr/sbin/nologin gaalop \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=maxima-build /maxima-root/usr/local/ /usr/local/

RUN mkdir -p /app/tools/maxima/bin \
    && ln -s /usr/local/bin/maxima /app/tools/maxima/bin/maxima

COPY --from=backend-build /workspace/gaalop-rest/target/gaalop-rest-1.0.0.jar /app/gaalop-rest.jar
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/start-container.sh /app/start-container.sh

RUN chmod +x /app/start-container.sh \
    && mkdir -p /app/logs /app/compile-history /var/lib/nginx /var/log/nginx \
    && chown -R gaalop:gaalop /app /var/lib/nginx /var/log/nginx /usr/share/nginx/html

ENV GAALOP_MAXIMA_COMMAND=tools/maxima/bin/maxima

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/v1/health || exit 1

USER gaalop

ENTRYPOINT ["/app/start-container.sh"]
