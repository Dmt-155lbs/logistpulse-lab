# LOGISTPULSE Lab

Laboratorio ejecutable basado en **LOGISTPULSE** (gestión de pedidos + flota de robots AMR de un almacén).
Pone en práctica todo el curso *API REST con Spring Boot, Jersey y Docker*: Jersey/JAX-RS, JPA, paginación y HATEOAS,
`RestClient`, pruebas, Docker Compose, y suma Redis, RabbitMQ y observabilidad (Prometheus + Grafana).

Los ejercicios guiados están en **[LAB.md](LAB.md)**.

## Qué hace

Un centro de distribución tiene una grilla con estanterías y zonas congestionadas, una flota de 5 AMR y pedidos.
Al **despachar** un pedido la API asigna un AMR libre con batería suficiente, calcula la ruta con **A\*** (costos ponderados),
pide el ETA a un transportista externo (WireMock; si no responde usa `costo de ruta × 2`) y registra el evento en una tabla *outbox* de la misma transacción; un relé lo publica en RabbitMQ y la "flota" lo consume (de forma idempotente) y pasa el pedido a `IN_TRANSIT`.

```
 Navegador / REST Client
        │  HTTP (Jersey, /api/**)
        ▼
 ┌────────────── API Spring Boot ──────────────┐      ┌───────────┐
 │ OrderResource  RobotResource  RouteResource │─JPA─▶│ Postgres  │
 │        OrderService (máquina de estados)    │      └───────────┘
 │  RoutePlanner (A*) ──@Cacheable────────────────▶  Redis
 │  CarrierClient (RestClient) ───────────────────▶  WireMock (transportista)
 │  OutboxService ─▶ OutboxPublisher (relé) ──────▶  RabbitMQ ─▶ FleetListener
 └──────────────── /actuator/prometheus ───────┘
                         │ scrape
                    Prometheus ─▶ Grafana
```

## Arranque en GitHub Codespaces

1. Sube esta carpeta a un repositorio de GitHub.
2. **Code ▸ Codespaces ▸ Create codespace on main** (máquina de 4 núcleos recomendada para el stack completo).
3. Espera a que termine el *postCreate* (descarga dependencias de Maven y deja Docker listo).

### Modo A — Lite (sin Docker, el más rápido)

H2 en memoria, caché local, RabbitMQ desactivado (el evento solo se escribe en el log).

```bash
./mvnw spring-boot:run     # Maven Wrapper: no hace falta tener Maven instalado (en Windows: .\mvnw.cmd)
```

Abre el puerto **8080** → consola web con el plano del almacén, flota y pedidos.

### Modo B — Stack completo

```bash
docker compose up -d --build
docker compose ps             # espera a que todo esté "healthy"/"running" (la API tiene healthcheck propio)
scripts/smoke.sh              # prueba de humo
```

| Servicio | Puerto | Notas |
|---|---|---|
| API + consola | 8080 | `/` consola, `/api/**` REST, `/actuator/**` |
| Grafana | 3000 | dashboard *LOGISTPULSE · Operaciones* ya provisionado (acceso anónimo) |
| Prometheus | 9090 | scrape de `api:8080/actuator/prometheus`; reglas de alerta en `infra/prometheus/rules.yml` (`/alerts`) |
| RabbitMQ | 15672 | consola, usuario `guest` / `guest` |
| WireMock | 9091 | transportista simulado, admin en `/__admin`. Códigos de pedido especiales: `LP-SLOW*` (tarda 5 s), `LP-FAIL5*` (siempre 503), `LP-BAD4*` (siempre 400), `LP-FLAKY*` (503, 503 y luego 200) |

### Modo C — Híbrido (ideal para depurar)

La API corre con Maven y solo el transportista simulado en Docker:

```bash
docker compose up -d carrier-mock
./mvnw spring-boot:run
```

## Endpoints principales

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders?status=&customer=&sort=&page=&size=` | Lista paginada (`size` ≤ 100; `sort` con lista blanca, p. ej. `createdAt,desc`) con enlaces `first/prev/next/last` |
| POST | `/api/orders` | Crea pedido → `201` + `Location` |
| GET | `/api/orders/{id}` | Detalle con enlaces según el estado (HATEOAS) |
| POST | `/api/orders/{id}/dispatch` | `CREATED → DISPATCHED`: asigna AMR, ruta y ETA |
| DELETE | `/api/orders/{id}` | Solo pedidos `CREATED` → `204`; en otro estado `409` |
| POST | `/api/orders/{id}/transitions` | `{"to":"IN_TRANSIT"\|"DELIVERED"\|"CANCELLED"\|"RETURNED"}` |
| GET | `/api/robots` · `/api/robots/{id}` · POST `/api/robots/{id}/charge` | Flota, un AMR (404 si no existe) y carga de batería |
| GET | `/api/routes?fromX&fromY&toX&toY` · `/api/routes/grid` | Ruta A* y plano |

Si una dependencia (BD, Redis) cae, la API responde `503` con JSON y `Retry-After`, no un 500. Más ejemplos en `requests.http` (extensión *REST Client*) y `postman/logistpulse.postman_collection.json` (con tests; ver *Pruebas*).

## Estructura

```
src/main/java/com/logistpulse/lab/
  api/          recursos Jersey, DTOs, mapeo de excepciones
  domain/       entidades JPA y máquina de estados (OrderStatus)
  repo/         Spring Data JPA (EntityGraph contra N+1)
  service/      reglas de negocio y transacciones
  route/        grilla, A*, caché de rutas
  integration/  RestClient hacia el transportista
  outbox/       patrón Outbox: evento en la misma transacción + relé hacia RabbitMQ
  messaging/    consumidor RabbitMQ idempotente (processed_message)
  config/       datos iniciales, métricas de flota y de pedidos, caché resiliente
infra/          WireMock, Prometheus (+ reglas de alerta), Grafana
```

## Pruebas

```bash
./mvnw test     # unitarias (A*, máquina de estados, servicio, cliente, outbox...): no necesitan Docker

# Colección de Postman desde la terminal (con la API arriba en :8080); no requiere la GUI
npx newman run postman/logistpulse.postman_collection.json
```

## Problemas frecuentes

- **`docker compose` falla en Codespaces** (red anidada / docker-in-docker): usa el Modo A o C; con `docker compose down -v` y volver a levantar se resuelven la mayoría de los estados corruptos. `docker info` debe responder sin error antes de empezar.
- **Puerto 8080 ocupado**: `server.port=8081 ./mvnw spring-boot:run` (o `SERVER_PORT=8081`).
- **Primer arranque lento**: Maven descarga dependencias; las siguientes ejecuciones usan caché.
- **Grafana sin datos**: genera tráfico con `scripts/smoke.sh` y espera ~10 s (scrape cada 5 s).
- **`/actuator/*` devuelve 404**: el laboratorio monta Jersey en `/` (`spring.jersey.application-path=/`); revisa que no lo hayas cambiado.
