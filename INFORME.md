# INFORME — Laboratorio LOGISTPULSE

Modo de ejecución: **Modo A** (`./mvnw spring-boot:run`, H2 en memoria, puerto 8080, sin Redis/RabbitMQ). Java 21, Maven Wrapper.

## Parte 0 · Recorrido

### Qué se hizo
- Lectura de `OrderResource`, `OrderService`, `OrderStatus` y de `JerseyConfig`.
- Ejecución de `scripts/smoke.sh` con la aplicación levantada y de `./mvnw -q test`.

### Flujo de `POST /api/orders/{id}/dispatch`

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente
    participant R as OrderResource (JAX-RS)
    participant S as OrderService (@Transactional)
    participant DB as OrderRepository / RobotRepository (JPA)
    participant P as RoutePlanner (A*)
    participant K as CarrierClient (HTTP)
    participant E as DispatchEventPublisher

    C->>R: POST /api/orders/{id}/dispatch
    R->>S: dispatch(id)
    S->>DB: findById(id)
    DB-->>S: Order (si no existe: ApiException 404)
    S->>S: status.canGoTo(DISPATCHED)? (si no: ApiException 409)
    S->>DB: findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(IDLE, 20)
    DB-->>S: AMR libre (si no hay: ApiException 409)
    S->>P: plan(origen del almacén, destino)
    P-->>S: RouteDto (costo)
    S->>K: etaMinutes(code)
    K-->>S: ETA, o vacío (timeout/caída) => costo*2
    S->>S: order -> DISPATCHED, robot asignado, robot -> BUSY
    S->>E: publishAfterCommit(orderId) (se ejecuta tras el commit)
    S-->>R: OrderDto
    R->>R: linked(): añade enlaces HATEOAS según estado
    R-->>C: 200 OK + JSON
    Note over S,DB: COMMIT: UPDATE orders y robot (con @Version)
    E-->>E: afterCommit: log [lite] o convertAndSend a RabbitMQ
```

### Cómo se probó
- `./mvnw -q test`: sin errores. Informes de surefire: `OrderStatusTest` 4 tests, `AStarPathfinderTest` 6 tests, 0 fallos.
- `bash scripts/smoke.sh` (app arriba): pasos 1–7 `ok` (200, 422, 201, 400, 200/409, 409). El paso 8 muestra el pedido 1 en `DISPATCHED`, robot `AMR-01`, `etaMinutes` 36 (el transportista en `localhost:9091` no está levantado, así que se usa el valor de respaldo `costo*2` = 18*2). El paso 9 devuelve 409, esperado en Modo A porque no hay consumidor RabbitMQ que pase el pedido a `IN_TRANSIT`. Terminó con "Smoke test terminado."

### ★ ¿Qué papel juega `JerseyConfig`? ¿Qué pasaría si olvidas registrar un recurso?
`JerseyConfig` extiende `ResourceConfig` y es el punto de configuración de Jersey: indica qué clases (`@Path` recursos y `@Provider` mappers) forman la aplicación JAX-RS. Spring Boot lo detecta por ser `@Component` y monta Jersey como servlet (`spring.jersey.application-path=/`). Que una clase sea `@Component` + `@Path` **no basta**: Jersey solo sirve lo que se registra explícitamente (no hay escaneo de paquetes aquí).

Verificación empírica: comenté `register(RobotResource.class)`, levanté la app y ejecuté `GET /api/robots`: respuesta **404 Not Found** (página HTML de Tomcat), sin ningún aviso o error en el log de arranque; mientras `GET /api/orders/1` seguía dando 200. Es decir, el fallo es silencioso y se manifiesta como 404. Lo mismo ocurre con un `ExceptionMapper` no registrado: la excepción no se traduce y se obtiene un 500 genérico. Se restauró el archivo después.

## Parte 1 · Jersey / JAX-RS

### Qué se implementó
- `GET /api/robots/{id}`: `RobotResource.get` (`@PathParam`) + `RobotService.get`; lanza `ApiException(404)` si no existe.
- `DELETE /api/orders/{id}`: `OrderResource.delete` devuelve 204; la regla de negocio está en `OrderService.delete` (solo `CREATED`; otro estado lanza `ApiException(409)`; inexistente 404).
- `api/ConstraintViolationMapper.java` (`ExceptionMapper<ConstraintViolationException>`), registrado en `JerseyConfig`. Devuelve 400 con `{"status":400,"error":"Validación fallida","violations":[{"field","message"}]}`. La ruta de la propiedad llega como `create.input.customer`, por lo que el mapper se queda con el último nodo (el nombre del campo).
- Test unitario `ConstraintViolationMapperTest` (sin contexto Spring).
- Ejemplos en `requests.http`.

### Cómo se probó
Antes de crear el mapper, un POST inválido (`{"code":"x","customer":"","destX":-1,"destY":0}`) ya daba 400, pero con **cuerpo HTML vacío de Tomcat** (`Content-Type: text/html`): Jersey tiene un mapper por defecto para `ValidationException` que fija el estado pero no entrega JSON. Con el mapper propio (más específico, registrado en `JerseyConfig`) la respuesta real fue:
```
HTTP/1.1 400
{"status":400,"violations":[{"message":"código con formato LP-XXXX","field":"code"},{"message":"no debe estar vacío","field":"customer"},{"message":"debe ser mayor que o igual a 0","field":"destX"}],"error":"Validación fallida"}
```
Resto de pruebas con curl (app en Modo A):
| Petición | Resultado |
|---|---|
| `GET /api/robots/2` | 200 `{"id":2,"code":"AMR-02",...}` |
| `GET /api/robots/99` | 404 `{"error":"Robot 99 no existe","status":404}` |
| `DELETE /api/orders/3` (CREATED) | 204; luego `GET /api/orders/3` = 404 |
| `DELETE /api/orders/1` (tras despacharlo) | 409 `Solo se pueden borrar pedidos CREATED; el pedido 1 está DISPATCHED` |
| `DELETE /api/orders/99` | 404 |

`./mvnw -q test`: 11 tests, 0 fallos (1 nuevo).

### ★ Equivalencias con Spring MVC
| JAX-RS / Jersey | Spring MVC |
|---|---|
| `@QueryParam("status")` (con `@DefaultValue`) | `@RequestParam(name = "status", defaultValue = "...")` (también `@PathVariable` para `@PathParam`) |
| `@Context UriInfo` (`getBaseUriBuilder()`, `getAbsolutePathBuilder()`) | `UriComponentsBuilder` como parámetro del método, `ServletUriComponentsBuilder.fromCurrentRequest()` / `fromCurrentContextPath()`, o `HttpServletRequest`; para enlaces a controladores, `MvcUriComponentsBuilder` |
| `ExceptionMapper<T>` + `@Provider` (registrado en `ResourceConfig`) | `@ExceptionHandler(T.class)` dentro de un controlador, o global con `@ControllerAdvice` / `@RestControllerAdvice` (y `ResponseEntityExceptionHandler`) |

Diferencia notable: en Jersey el mapper debe registrarse a mano; en Spring MVC `@ControllerAdvice` se descubre por escaneo de componentes.
