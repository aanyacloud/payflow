package com.payflow.backend.dto;

public record ApiResponse(
        String message,
        String application,
        String status
) {}
