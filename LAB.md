# Guía de laboratorio — LOGISTPULSE

Duración estimada: 3–4 horas. Requisitos: haber leído los módulos del curso indicados en cada parte.
Entregable sugerido: tu repositorio con los cambios + un `INFORME.md` corto con las respuestas a las preguntas ★.

> Antes de empezar: `mvn spring-boot:run` (Modo A) y abre el puerto 8080. Haz clic en el plano, crea un pedido y despáchalo.

---

## Parte 0 · Recorrido (20 min) — *Estructura del proyecto*

1. Lee `OrderResource`, `OrderService` y `OrderStatus`. Dibuja el flujo de `POST /api/orders/{id}/dispatch`.
2. Ejecuta `scripts/smoke.sh` y `mvn test`.
3. ★ ¿Qué papel juega `JerseyConfig`? ¿Qué pasaría si olvidas registrar un recurso?

## Parte 1 · Jersey / JAX-RS (30 min) — *Spring Boot con Jersey y JAX-RS*

1. Añade `GET /api/robots/{id}` con `@PathParam` y 404 si no existe (usa `ApiException`).
2. Añade `DELETE /api/orders/{id}` que solo permita borrar pedidos `CREATED` (204, o 409 en otro caso).
3. Añade un `ExceptionMapper<ConstraintViolationException>` que devuelva 400 con la lista de campos inválidos.
4. ★ Equivalencias: ¿qué anotación de Spring MVC corresponde a `@QueryParam`, `@Context UriInfo` y `ExceptionMapper`?

## Parte 2 · JPA e Hibernate (30 min) — *JPA, N+1*

1. En `application.properties` pon `spring.jpa.show-sql=true`. Lista pedidos con `GET /api/orders?size=10`; cuenta las consultas.
2. Quita `@EntityGraph` de `OrderRepository.findAll`. Repite y vuelve a contar. ★ ¿Cuántas consultas aparecen y por qué?
3. Restaura el `@EntityGraph`. Añade un índice (`@Table(indexes = ...)`) sobre `status` y justifícalo.

## Parte 3 · Spring Data, paginación y HATEOAS (30 min)

1. Añade filtro `?customer=` (contiene, sin distinguir mayúsculas) con un método derivado del repositorio.
2. Añade `?sort=createdAt,desc` validando contra una lista blanca de campos.
3. ★ Los enlaces de `OrderDto` cambian según el estado. Dibuja el grafo de estados con los enlaces que ofrece cada uno.
4. Reto: ¿por qué `size` está limitado a 100 en el servicio y no solo en el recurso?

## Parte 4 · Máquina de estados y concurrencia (30 min) — *Formal Decision Gate*

1. Añade el estado `RETURNED` (desde `DELIVERED`) y ajusta `OrderStatus`. Amplía `OrderStatusTest` con ≥ 4 casos.
2. **Concurrencia**: lanza dos despachos simultáneos del mismo pedido:
   ```bash
   curl -s -X POST localhost:8080/api/orders/2/dispatch & curl -s -X POST localhost:8080/api/orders/2/dispatch & wait
   ```
   ★ ¿Cuál gana y qué recibe el perdedor (`@Version`, `OptimisticLockMapper`)? Intenta provocar que dos pedidos distintos reciban el mismo AMR.
3. Escribe en una tabla las invariantes del sistema (p. ej. "un AMR `BUSY` tiene exactamente un pedido `DISPATCHED/IN_TRANSIT`") y di cuáles **no** están garantizadas por la base de datos todavía.

## Parte 5 · Rutas A*, Redis y métricas (40 min)

1. Con el plano de la consola, compara una ruta que atraviesa la zona congestionada con una que la rodea.
2. Modo B: pide dos veces la misma ruta y observa `logistpulse_route_plan_seconds_count` en `/actuator/prometheus`. ★ ¿Por qué solo sube la primera vez? Comprueba la clave con `docker compose exec redis redis-cli keys '*'`.
3. Cambia el costo de la zona congestionada de 5 a 2 en `WarehouseGrid` y actualiza `AStarPathfinderTest`. ★ ¿Sigue siendo admisible la heurística Manhattan? ¿Y si el costo mínimo fuera 0,5?
4. Reto: añade diagonales (8 vecinos) con costo √2 y adapta la heurística.
5. Reto: la caché nunca se invalida. ¿Qué ocurre si cambia el plano? Diseña la estrategia (TTL, versión del plano en la clave, `@CacheEvict`).

## Parte 6 · RestClient y resiliencia (40 min) — *RestClient*

1. Crea un pedido con código `LP-SLOW1` y despáchalo (usa `requests.http`). WireMock tarda 5 s; `CarrierClient` corta a los 2 s.
   ★ ¿Qué ETA queda y de dónde sale? Revisa el log: ¿se avisa de la degradación?
2. Apaga el transportista: `docker compose stop carrier-mock` y despacha otro pedido. Vuelve a encenderlo.
3. ★ **Trampa deliberada**: `OrderService.dispatch` hace una llamada HTTP *dentro* de una transacción. Describe el riesgo (conexiones de la pool retenidas, bloqueos) y refactoriza para consultar el ETA **antes** de abrir la transacción.
4. Reto: añade reintento con backoff (máx. 2) solo para errores 5xx, sin reintentar los 4xx.

## Parte 7 · Mensajería, consistencia y Outbox (40 min) — *Transaction Crisis Lab*

1. Modo B: despacha un pedido y observa en el log `Evento order.dispatched publicado` y luego `Flota recibió …`. En la consola de RabbitMQ (15672) mira la cola `orders.dispatched`.
2. Para el consumidor (comenta `@RabbitListener`), despacha 3 pedidos y mira cómo se acumulan mensajes. Reactívalo.
3. ★ El publicador envía **después del commit** (`afterCommit`). Explica qué fallo evita y cuál **sigue** existiendo (el proceso cae entre el commit y el envío).
4. Implementa el patrón **Outbox**: tabla `outbox_event`, se inserta en la misma transacción que el cambio de estado y un `@Scheduled` la publica y marca como enviada.
5. ★ El consumidor puede recibir el mismo mensaje dos veces. Hazlo **idempotente** (¿qué pasa hoy si llega dos veces `IN_TRANSIT`?).

## Parte 8 · Observabilidad y "guerra de resiliencia" (40 min) — *Resilient Operations Console*

1. Abre Grafana (3000) → *LOGISTPULSE · Operaciones*. Genera carga:
   ```bash
   for i in $(seq 1 40); do curl -s -X POST -H 'Content-Type: application/json' \
     -d '{"customer":"carga","destX":10,"destY":5}' localhost:8080/api/orders >/dev/null; done
   ```
2. Despacha pedidos hasta agotar la flota. ★ ¿Qué estado HTTP recibes y qué panel lo muestra? Carga baterías con `POST /api/robots/{id}/charge`.
3. **Caos** (uno a la vez, anota qué panel/log cambia y cuánto tarda en recuperarse):
   - `docker compose stop rabbitmq` → despacha → `docker compose start rabbitmq`
   - `docker compose stop redis` → pide rutas
   - `docker compose stop carrier-mock` → despacha
   - `docker compose stop db` → `GET /actuator/health`
4. Añade al dashboard un panel de latencia p95 de `http_server_requests` (activa `management.metrics.distribution.percentiles-histogram.http.server.requests=true`).
5. ★ Define 3 SLOs (disponibilidad del despacho, latencia p95, tiempo máximo en `DISPATCHED`) y escribe la consulta PromQL de cada uno.
6. Reto: añade `logistpulse_orders_by_status` (gauge por estado) y una alerta "AMR libres = 0 durante 1 minuto".

## Parte 9 · Pruebas y contenedores (30 min) — *Pruebas con Postman y JUnit; Docker*

1. Importa `postman/logistpulse.postman_collection.json`, ejecuta el *Runner* y añade 3 *tests* de Postman (código de estado, `Location`, esquema de la respuesta).
2. Escribe un test de Mockito para `OrderService.dispatch` que verifique: sin robots → 409; pedido ya despachado → 409; con ETA caído se usa `route.cost()*2`.
3. ★ Revisa el `Dockerfile`: ¿por qué dos etapas? ¿qué se cachea con `dependency:go-offline`? ¿por qué `USER appuser`?
4. Reto: añade un `healthcheck` al servicio `api` en el compose (no hay `curl` en la imagen JRE: ¿qué alternativa usas?).

---

## Proyecto de cierre (opcional)

Elige **uno** y documéntalo con un ADR (Architecture Decision Record):

- **Flyway** en lugar de `ddl-auto=update`, con una restricción `CHECK` que garantice la invariante de la Parte 4.
- **Reasignación**: si un AMR baja de 10 % de batería en misión, reasigna el pedido a otro AMR por evento.
- **Seguridad**: protege `/api/**` con una clave de API y deja `/actuator/health` público.
- **Puente a 3D**: exporta la ruta A* como JSON para animarla en un gemelo digital (p. ej. Unreal Engine).

## Rúbrica orientativa

| Criterio | Peso |
|---|---|
| Partes 1–3 completas y probadas | 25 % |
| Concurrencia y máquina de estados (Parte 4) | 20 % |
| Resiliencia: RestClient, Outbox, idempotencia (Partes 6–7) | 25 % |
| Observabilidad y análisis de caos (Parte 8) | 20 % |
| Calidad: pruebas, commits, informe | 10 % |
