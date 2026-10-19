package com.drini.wallet.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One line of the managers' error log (GET /api/v1/admin/errors). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEventResponse {

    private long id;

    private Instant occurredAt;

    private String service;

    private int httpStatus;

    private String code;

    private String message;

    private String path;
}
