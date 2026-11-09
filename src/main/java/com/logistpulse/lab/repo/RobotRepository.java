package com.logistpulse.lab.repo;

import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    List<Robot> findByStatusAndBatteryGreaterThanEqualOrderByIdAsc(RobotStatus status, int minBattery);

    long countByStatus(RobotStatus status);
}
