package com.logistpulse.lab.api.dto;

import com.logistpulse.lab.domain.Robot;
import com.logistpulse.lab.domain.RobotStatus;

public record RobotDto(Long id, String code, int x, int y, int battery, RobotStatus status) {

    public static RobotDto from(Robot r) {
        return new RobotDto(r.getId(), r.getCode(), r.getX(), r.getY(), r.getBattery(), r.getStatus());
    }
}
