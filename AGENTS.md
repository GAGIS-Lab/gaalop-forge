# Repository Guidelines

## Project Structure & Module Organization

Gaalop is a Maven reactor project. Shared compiler APIs live in `api/`; parsing and optimization stages are in `clucalc/`, `algebra/`, `tba/`, and `gapp/`; output plugins use `codegen-*` modules. User entry points are `cli/`, `gui/`, and the Spring Boot service in `gaalop-rest/`. The Vue 3/Vite client is maintained separately in `frontend/`. Java sources follow Maven layout (`src/main/java`, `src/main/resources`, and `src/test/java`); broader compiler tests are concentrated in `testbenchTbaGapp/`. Deployment files are under `docker/`, `deploy/`, `Dockerfile`, and `docker-compose.yml`.

## Build, Test, and Development Commands

- `mvn clean test`: compile the full reactor and run JUnit tests.
- `mvn clean package`: build all modules and distributable artifacts.
- `mvn -pl gaalop-rest -am package`: build the REST service plus required modules. Use JDK 17 for this module; most legacy modules target Java 8 bytecode.
- `java -jar gaalop-rest/target/gaalop-rest-1.0.0.jar`: run the packaged API on port 8080.
- `cd frontend && pnpm install && pnpm dev`: install locked frontend dependencies and start Vite on port 5173, proxying `/api` to the backend.
- `cd frontend && pnpm build`: produce the production client in `frontend/dist/`.
- `docker compose up -d --build`: build and launch the integrated stack.

## Coding Style & Naming Conventions

Use four-space indentation in Java, UTF-8, `de.gaalop...` packages, PascalCase types, camelCase methods/fields, and uppercase constants. Keep plugin implementations within their owning module and register service providers under `src/main/resources/META-INF/services`. Vue and JavaScript use two spaces, double quotes, and PascalCase component files. No repository-wide formatter is configured; match surrounding code and avoid formatting unrelated lines. Manage frontend dependencies only with `pnpm add` or `pnpm add -D`, committing both `package.json` and `pnpm-lock.yaml`.

## Testing Guidelines

Tests use JUnit 4. Place focused tests beside the affected module and name classes `*Test.java`; use `testbenchTbaGapp` for cross-stage compiler behavior. Run `mvn -pl <module> -am test` while iterating, then `mvn clean test` before submission. The frontend has no automated test script currently, so verify `pnpm build` and manually exercise changed UI flows.

## Commit & Pull Request Guidelines

History favors short imperative subjects such as `Add Gaalop web deployment`; keep each commit scoped to one concern. Pull requests should explain behavior and affected modules, link relevant issues, list verification commands, and include screenshots for UI changes. Call out API or configuration changes and never commit secrets, local IDE state, generated `target/`, `frontend/dist/`, or runtime output.
