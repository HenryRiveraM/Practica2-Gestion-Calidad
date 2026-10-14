package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.dto.CheckInRequest;
import com.cocacola.eventintelligence.dto.CheckInResponse;
import com.cocacola.eventintelligence.entity.Attendance;
import com.cocacola.eventintelligence.service.AttendanceService;
import com.cocacola.eventintelligence.service.CheckInService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendances")
public class AttendanceController extends BaseCrudController<Attendance> {
    private final CheckInService checkInService;

    public AttendanceController(AttendanceService attendanceService, CheckInService checkInService) {
        super(attendanceService);
        this.checkInService = checkInService;
    }

    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(checkInService.checkIn(request));
    }
}
