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
RUN pnpm build

FROM eclipse-temurin:17-jre-jammy

RUN apt-get -o Acquire::Retries=5 update \
    && apt-get -o Acquire::Retries=5 install -y --no-install-recommends ca-certificates nginx maxima \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p /app/tools/maxima/bin \
    && ln -s /usr/bin/maxima /app/tools/maxima/bin/maxima

COPY --from=backend-build /workspace/gaalop-rest/target/gaalop-rest-1.0.0.jar /app/gaalop-rest.jar
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/start-container.sh /app/start-container.sh

RUN chmod +x /app/start-container.sh

ENV GAALOP_MAXIMA_COMMAND=tools/maxima/bin/maxima

EXPOSE 8080

ENTRYPOINT ["/app/start-container.sh"]
