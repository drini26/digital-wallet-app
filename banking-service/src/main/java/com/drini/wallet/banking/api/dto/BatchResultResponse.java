package com.drini.wallet.banking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** What one batch run did: how many transfers were sent, will be retried, were rejected, or skipped (frozen). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchResultResponse {

    private UUID batchId;

    private int sent;

    private int retrying;

    private int rejected;

    private int skippedFrozen;
}
