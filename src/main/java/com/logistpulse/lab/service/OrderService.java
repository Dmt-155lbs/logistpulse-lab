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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    /** Un AMR con menos batería que esto no recibe pedidos. */
    static final int MIN_BATTERY = 20;

    private final OrderRepository orders;
    private final RobotRepository robots;
    private final WarehouseRepository warehouses;
    private final RoutePlanner planner;
    private final CarrierClient carrier;
    private final DispatchEventPublisher events;
    private final Counter created;
    private final Counter dispatched;

    public OrderService(OrderRepository orders, RobotRepository robots, WarehouseRepository warehouses,
                        RoutePlanner planner, CarrierClient carrier, DispatchEventPublisher events,
                        MeterRegistry registry) {
        this.orders = orders;
        this.robots = robots;
        this.warehouses = warehouses;
        this.planner = planner;
        this.carrier = carrier;
        this.events = events;
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
    public PageDto<OrderDto> list(OrderStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("id").descending());
        Page<Order> result = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        List<OrderDto> content = result.getContent().stream().map(OrderDto::from).toList();
        return new PageDto<>(content, safePage, safeSize, result.getTotalElements(), result.getTotalPages(), java.util.Map.of());
    }

    /**
     * CREATED → DISPATCHED: asigna el AMR libre con batería suficiente, calcula ruta y ETA y publica el evento.
     * Ojo (ejercicio): hay una llamada HTTP al transportista dentro de la transacción.
     */
    @Transactional
    public OrderDto dispatch(Long id) {
        Order order = find(id);
        if (!order.getStatus().canGoTo(OrderStatus.DISPATCHED)) {
            throw new ApiException(409, "Transición ilegal " + order.getStatus() + " → DISPATCHED");
        }
        Robot robot = robots.findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus.IDLE, MIN_BATTERY)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(409, "No hay AMR disponible (libre y con batería ≥ " + MIN_BATTERY + "%)"));

        Warehouse w = order.getWarehouse();
        RouteDto route = planner.plan(new Point(w.getOriginX(), w.getOriginY()),
                new Point(order.getDestX(), order.getDestY()));
        int eta = carrier.etaMinutes(order.getCode()).orElse(route.cost() * 2);

        order.setStatus(OrderStatus.DISPATCHED);
        order.setRobot(robot);
        order.setEtaMinutes(eta);
        robot.setStatus(RobotStatus.BUSY);

        dispatched.increment();
        events.publishAfterCommit(order.getId());
        return OrderDto.from(order);
    }

    /** Transiciones restantes: IN_TRANSIT, DELIVERED, CANCELLED. Valida contra la máquina de estados. */
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
