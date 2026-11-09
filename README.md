# LOGISTPULSE Lab

Laboratorio ejecutable basado en **LOGISTPULSE** (gestión de pedidos + flota de robots AMR de un almacén).
Pone en práctica todo el curso *API REST con Spring Boot, Jersey y Docker*: Jersey/JAX-RS, JPA, paginación y HATEOAS,
`RestClient`, pruebas, Docker Compose, y suma Redis, RabbitMQ y observabilidad (Prometheus + Grafana).

Los ejercicios guiados están en **[LAB.md](LAB.md)**.

## Qué hace

Un centro de distribución tiene una grilla con estanterías y zonas congestionadas, una flota de 5 AMR y pedidos.
Al **despachar** un pedido la API asigna un AMR libre con batería suficiente, calcula la ruta con **A\*** (costos ponderados),
pide el ETA a un transportista externo (WireMock) y publica un evento en RabbitMQ; la "flota" lo consume y pasa el pedido a `IN_TRANSIT`.

```
 Navegador / REST Client
        │  HTTP (Jersey, /api/**)
        ▼
 ┌────────────── API Spring Boot ──────────────┐      ┌───────────┐
 │ OrderResource  RobotResource  RouteResource │─JPA─▶│ Postgres  │
 │        OrderService (máquina de estados)    │      └───────────┘
 │  RoutePlanner (A*) ──@Cacheable────────────────▶  Redis
 │  CarrierClient (RestClient) ───────────────────▶  WireMock (transportista)
 │  DispatchEventPublisher ───after-commit────────▶  RabbitMQ ─▶ FleetListener
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
mvn spring-boot:run
```

Abre el puerto **8080** → consola web con el plano del almacén, flota y pedidos.

### Modo B — Stack completo

```bash
docker compose up -d --build
docker compose ps             # espera a que todo esté "healthy"/"running"
scripts/smoke.sh              # prueba de humo
```

| Servicio | Puerto | Notas |
|---|---|---|
| API + consola | 8080 | `/` consola, `/api/**` REST, `/actuator/**` |
| Grafana | 3000 | dashboard *LOGISTPULSE · Operaciones* ya provisionado (acceso anónimo) |
| Prometheus | 9090 | scrape de `api:8080/actuator/prometheus` |
| RabbitMQ | 15672 | consola, usuario `guest` / `guest` |
| WireMock | 9091 | transportista simulado, admin en `/__admin` |

### Modo C — Híbrido (ideal para depurar)

La API corre con Maven y solo el transportista simulado en Docker:

```bash
docker compose up -d carrier-mock
mvn spring-boot:run
```

## Endpoints principales

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders?status=&page=&size=` | Lista paginada con enlaces `first/prev/next/last` |
| POST | `/api/orders` | Crea pedido → `201` + `Location` |
| GET | `/api/orders/{id}` | Detalle con enlaces según el estado (HATEOAS) |
| POST | `/api/orders/{id}/dispatch` | `CREATED → DISPATCHED`: asigna AMR, ruta y ETA |
| POST | `/api/orders/{id}/transitions` | `{"to":"IN_TRANSIT"\|"DELIVERED"\|"CANCELLED"}` |
| GET | `/api/robots` · POST `/api/robots/{id}/charge` | Flota y carga de batería |
| GET | `/api/routes?fromX&fromY&toX&toY` · `/api/routes/grid` | Ruta A* y plano |

Más ejemplos en `requests.http` (extensión *REST Client*) y `postman/logistpulse.postman_collection.json`.

## Estructura

```
src/main/java/com/logistpulse/lab/
  api/          recursos Jersey, DTOs, mapeo de excepciones
  domain/       entidades JPA y máquina de estados (OrderStatus)
  repo/         Spring Data JPA (EntityGraph contra N+1)
  service/      reglas de negocio y transacciones
  route/        grilla, A*, caché de rutas
  integration/  RestClient hacia el transportista
  messaging/    publicador after-commit + consumidor RabbitMQ
  config/       datos iniciales, métricas de flota
infra/          WireMock, Prometheus, Grafana
```

## Pruebas

```bash
mvn test        # A* y máquina de estados (no necesitan Docker)
```

## Problemas frecuentes

- **`docker compose` falla en Codespaces** (red anidada / docker-in-docker): usa el Modo A o C; con `docker compose down -v` y volver a levantar se resuelven la mayoría de los estados corruptos. `docker info` debe responder sin error antes de empezar.
- **Puerto 8080 ocupado**: `server.port=8081 mvn spring-boot:run` (o `SERVER_PORT=8081`).
- **Primer arranque lento**: Maven descarga dependencias; las siguientes ejecuciones usan caché.
- **Grafana sin datos**: genera tráfico con `scripts/smoke.sh` y espera ~10 s (scrape cada 5 s).
- **`/actuator/*` devuelve 404**: el laboratorio monta Jersey en `/` (`spring.jersey.application-path=/`); revisa que no lo hayas cambiado.
