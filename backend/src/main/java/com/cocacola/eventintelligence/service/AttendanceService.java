package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Attendance;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

@Service
public class AttendanceService extends AbstractCrudService<Attendance> {
    public AttendanceService(AttendanceRepository repository) {
        super(repository, "Asistencia");
    }
}