package com.logistpulse.lab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** "order" es palabra reservada en SQL, por eso la tabla se llama orders. */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String customerName;

    private int destX;
    private int destY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    private Robot robot;

    private Integer etaMinutes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    protected Order() {
    }

    public Order(String code, String customerName, int destX, int destY, Warehouse warehouse) {
        this.code = code;
        this.customerName = customerName;
        this.destX = destX;
        this.destY = destY;
        this.warehouse = warehouse;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getCustomerName() { return customerName; }
    public int getDestX() { return destX; }
    public int getDestY() { return destY; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Warehouse getWarehouse() { return warehouse; }
    public Robot getRobot() { return robot; }
    public void setRobot(Robot robot) { this.robot = robot; }
    public Integer getEtaMinutes() { return etaMinutes; }
    public void setEtaMinutes(Integer etaMinutes) { this.etaMinutes = etaMinutes; }
    public Instant getCreatedAt() { return createdAt; }
}
