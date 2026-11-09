package com.logistpulse.lab.config;

import com.logistpulse.lab.domain.Order;
import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.Warehouse;
import com.logistpulse.lab.repo.OrderRepository;
import com.logistpulse.lab.repo.RobotRepository;
import com.logistpulse.lab.repo.WarehouseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Datos iniciales: 1 almacén, 5 AMR (el último con batería baja a propósito) y 3 pedidos. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final WarehouseRepository warehouses;
    private final RobotRepository robots;
    private final OrderRepository orders;

    public DataSeeder(WarehouseRepository warehouses, RobotRepository robots, OrderRepository orders) {
        this.warehouses = warehouses;
        this.robots = robots;
        this.orders = orders;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (warehouses.count() > 0) {
            return;
        }
        Warehouse w = warehouses.save(new Warehouse("CD Quito Norte", 0, 0));
        int[] batteries = {100, 90, 80, 70, 15};
        for (int i = 0; i < batteries.length; i++) {
            robots.save(new Robot(String.format("AMR-%02d", i + 1), w, batteries[i]));
        }
        orders.save(new Order("LP-DEMO01", "Supermaxi Cumbayá", 11, 7, w));
        orders.save(new Order("LP-DEMO02", "Farmacia Cruz Azul", 5, 3, w));
        orders.save(new Order("LP-DEMO03", "Ferretería La 10", 10, 5, w));
    }
}
