package com.logistpulse.lab.service;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.api.dto.OrderDto;
import com.logistpulse.lab.api.dto.OrderInput;
import com.logistpulse.lab.api.dto.PageDto;
import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.OrderStatus;
import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.domain.Warehouse;
import com.logistpulse.lab.integration.CarrierClient;
import com.logistpulse.lab.messaging.DispatchEventPublisher;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import com.logistpulse.lab.repo.WarehouseRepository;
import com.logistpulse.lab.route.Point;
import com.logistpulse.lab.route.RouteDto;
import com.logistpulse.lab.route.RoutePlanner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Un AMR con menos batería que esto no recibe pedidos. */
    static final int MIN_BATTERY = 20;

    /** Tamaño máximo de página permitido. */
    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orders;
    private final RobotRepository robots;
    private final WarehouseRepository warehouses;
    private final RoutePlanner planner;
    private final CarrierClient carrier;
    private final DispatchEventPublisher events;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    private final Counter created;
    private final Counter dispatched;

    public OrderService(OrderRepository orders, RobotRepository robots, WarehouseRepository warehouses,
                        RoutePlanner planner, CarrierClient carrier, DispatchEventPublisher events,
                        PlatformTransactionManager tm, MeterRegistry registry) {
        this.orders = orders;
        this.robots = robots;
        this.warehouses = warehouses;
        this.planner = planner;
        this.carrier = carrier;
        this.events = events;
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(tm);
        this.created = Counter.builder("logistpulse.orders.created").description("Pedidos creados").register(registry);
        this.dispatched = Counter.builder("logistpulse.orders.dispatched").description("Pedidos despachados").register(registry);
    }

    @Transactional
    public OrderDto create(OrderInput in) {
        Warehouse warehouse = warehouses.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(500, "No hay almacén configurado"));
        if (!planner.isWalkable(in.destX(), in.destY())) {
            throw new ApiException(422, "Destino (" + in.destX() + "," + in.destY() + ") bloqueado o fuera de la grilla");
        }
        String code = in.code() != null ? in.code() : newCode();
        if (orders.existsByCode(code)) {
            throw new ApiException(409, "Ya existe un pedido con código " + code);
        }
        Order saved = orders.save(new Order(code, in.customer(), in.destX(), in.destY(), warehouse));
        created.increment();
        return OrderDto.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return OrderDto.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageDto<OrderDto> list(OrderStatus status, String customer, String sort, int page, int size) {
        // el límite vive aquí (no solo en el recurso) para que cualquier llamador del servicio lo respete
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, OrderSorting.parse(sort));
        boolean byCustomer = customer != null && !customer.isBlank();
        String term = byCustomer ? customer.trim() : null;
        Page<Order> result;
        if (byCustomer) {
            result = status == null
                    ? orders.findByCustomerNameContainingIgnoreCase(term, pageable)
                    : orders.findByStatusAndCustomerNameContainingIgnoreCase(status, term, pageable);
        } else {
            result = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        }
        List<OrderDto> content = result.getContent().stream().map(OrderDto::from).toList();
        return new PageDto<>(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages(), java.util.Map.of());
    }

    /** Solo se pueden borrar pedidos CREATED (aún sin AMR asignado). */
    @Transactional
    public void delete(Long id) {
        Order order = find(id);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new ApiException(409, "Solo se pueden borrar pedidos CREATED; el pedido " + id + " está " + order.getStatus());
        }
        orders.delete(order);
    }

    /** Datos mínimos que necesita la consulta al transportista; se leen en una transacción corta y de solo lectura. */
    private record DispatchPlan(String code, RouteDto route) {
    }

    /**
     * CREATED → DISPATCHED: asigna el AMR libre con batería suficiente, calcula ruta y ETA y publica el evento.
     *
     * Este método NO es @Transactional a propósito (Parte 6.3). Antes la llamada HTTP al transportista (hasta 2 s de
     * timeout, más con reintentos) ocurría dentro de la transacción: durante ese tiempo se retenía una conexión de la
     * pool de Hikari (10 por defecto; 10 despachos lentos simultáneos agotan la pool y TODAS las demás peticiones
     * esperan hasta 30 s por una conexión), la transacción vivía más de lo necesario y, con bloqueo optimista, cuanto
     * más larga la ventana mayor la probabilidad de conflicto @Version. Ahora hay tres pasos:
     *  1. transacción corta de solo lectura: valida (pedido existe y está CREATED, hay AMR libre) y calcula la ruta;
     *  2. SIN transacción ni conexión: consulta el ETA al transportista;
     *  3. transacción corta de escritura: RE-valida (otro hilo pudo despachar el pedido o tomar el AMR mientras tanto),
     *     asigna y registra el envío del evento tras el commit. @Version sigue siendo la red de seguridad ante la carrera.
     * (Se usa TransactionTemplate: un @Transactional en un método privado/interno del mismo bean no pasaría por el
     * proxy y no abriría transacción.)
     */
    public OrderDto dispatch(Long id) {
        DispatchPlan plan = readTx.execute(status -> {
            Order order = find(id);
            requireDispatchable(order);
            requireFreeRobot();
            Warehouse w = order.getWarehouse();
            RouteDto route = planner.plan(new Point(w.getOriginX(), w.getOriginY()),
                    new Point(order.getDestX(), order.getDestY()));
            return new DispatchPlan(order.getCode(), route);
        });

        // Sin transacción abierta: la conexión de la pool ya fue devuelta.
        int eta = carrier.etaMinutes(plan.code()).orElseGet(() -> {
            int local = plan.route().cost() * 2;
            log.warn("Pedido {}: ETA de contingencia {} min (costo de ruta {} x 2) porque el transportista no respondió",
                    plan.code(), local, plan.route().cost());
            return local;
        });

        return writeTx.execute(status -> {
            Order order = find(id);
            requireDispatchable(order);   // re-validación: el estado pudo cambiar durante la llamada HTTP
            Robot robot = requireFreeRobot();
            order.setStatus(OrderStatus.DISPATCHED);
            order.setRobot(robot);
            order.setEtaMinutes(eta);
            robot.setStatus(RobotStatus.BUSY);
            dispatched.increment();
            events.publishAfterCommit(order.getId());   // se enviará tras el commit de ESTA transacción
            return OrderDto.from(order);
        });
    }

    private void requireDispatchable(Order order) {
        if (!order.getStatus().canGoTo(OrderStatus.DISPATCHED)) {
            throw new ApiException(409, "Transición ilegal " + order.getStatus() + " → DISPATCHED");
        }
    }

    private Robot requireFreeRobot() {
        return robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, MIN_BATTERY)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(409, "No hay AMR disponible (libre y con batería ≥ " + MIN_BATTERY + "%)"));
    }

    /**
     * Transiciones restantes: IN_TRANSIT, DELIVERED, CANCELLED, RETURNED. RETURNED (devolución del cliente) no toca
     * al robot: al entregar ya quedó IDLE y el pedido conserva la referencia solo como histórico; liberar o reasignar
     * ese robot aquí podría pisar una misión posterior. Valida contra la máquina de estados. */
    @Transactional
    public OrderDto transition(Long id, OrderStatus to) {
        if (to == OrderStatus.DISPATCHED) {
            throw new ApiException(400, "Para despachar usa POST /api/orders/{id}/dispatch");
        }
        Order order = find(id);
        if (!order.getStatus().canGoTo(to)) {
            throw new ApiException(409, "Transición ilegal " + order.getStatus() + " → " + to);
        }
        Robot robot = order.getRobot();
        if (to == OrderStatus.DELIVERED && robot != null) {
            Warehouse w = order.getWarehouse();
            RouteDto route = planner.plan(new Point(w.getOriginX(), w.getOriginY()),
                    new Point(order.getDestX(), order.getDestY()));
            robot.setBattery(robot.getBattery() - route.cost());   // 1% de batería por unidad de costo (ida y vuelta)
            robot.setStatus(RobotStatus.IDLE);
        }
        if (to == OrderStatus.CANCELLED && robot != null) {
            robot.setStatus(RobotStatus.IDLE);
            order.setRobot(null);
        }
        order.setStatus(to);
        return OrderDto.from(order);
    }

    private Order find(Long id) {
        return orders.findById(id).orElseThrow(() -> new ApiException(404, "Pedido " + id + " no existe"));
    }

    private static String newCode() {
        return "LP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
