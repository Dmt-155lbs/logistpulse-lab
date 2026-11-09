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
import jakarta.persistence.Version;

/** Robot móvil autónomo (AMR). */
@Entity
public class Robot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse warehouse;

    private int x;
    private int y;

    /** 0..100 */
    private int battery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RobotStatus status = RobotStatus.IDLE;

    /** Bloqueo optimista: evita que dos despachos concurrentes tomen el mismo robot. */
    @Version
    private Long version;

    protected Robot() {
    }

    public Robot(String code, Warehouse warehouse, int battery) {
        this.code = code;
        this.warehouse = warehouse;
        this.x = warehouse.getOriginX();
        this.y = warehouse.getOriginY();
        this.battery = battery;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Warehouse getWarehouse() { return warehouse; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getBattery() { return battery; }
    public void setBattery(int battery) { this.battery = Math.max(0, Math.min(100, battery)); }
    public RobotStatus getStatus() { return status; }
    public void setStatus(RobotStatus status) { this.status = status; }
}
