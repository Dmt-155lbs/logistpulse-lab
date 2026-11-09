package com.logistpulse.lab.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private int originX;
    private int originY;

    protected Warehouse() {
    }

    public Warehouse(String name, int originX, int originY) {
        this.name = name;
        this.originX = originX;
        this.originY = originY;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
}
