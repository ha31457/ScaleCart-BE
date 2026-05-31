package com.hss.scalecart.controller;

import com.hss.scalecart.dto.response.ApiResponse;
import com.hss.scalecart.dto.response.DeadLetterEventResponse;
import com.hss.scalecart.dto.response.PagedResponse;
import com.hss.scalecart.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<PagedResponse<DeadLetterEventResponse>>> getDlqEvents(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDlqEvents(cursor, size), "DLQ events fetched"));
    }

    @PostMapping("/dlq/{id}/requeue")
    public ResponseEntity<ApiResponse<Void>> requeueEvent(@PathVariable UUID id) {
        adminService.requeueEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Event requeued successfully"));
    }
}