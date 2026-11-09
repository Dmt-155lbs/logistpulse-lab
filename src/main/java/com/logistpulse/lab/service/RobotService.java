package com.logistpulse.lab.service;

import com.logistpulse.lab.api.ApiException;
import com.logistpulse.lab.api.dto.RobotDto;
import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;
import com.logistpulse.lab.repo.RobotRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotService {

    private final RobotRepository robots;

    public RobotService(RobotRepository robots) {
        this.robots = robots;
    }

    @Transactional(readOnly = true)
    public List<RobotDto> list() {
        return robots.findAll().stream().map(RobotDto::from).toList();
    }

    @Transactional
    public RobotDto charge(Long id) {
        Robot robot = robots.findById(id).orElseThrow(() -> new ApiException(404, "Robot " + id + " no existe"));
        if (robot.getStatus() == RobotStatus.BUSY) {
            throw new ApiException(409, "El robot " + robot.getCode() + " está en misión; no puede cargar");
        }
        robot.setBattery(100);
        return RobotDto.from(robot);
    }
}
