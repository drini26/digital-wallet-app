package com.drini.wallet.banking.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of PUT /api/v1/admin/mock-bank/settings: the bank's mode and how long it waits before settling. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MockBankSettingsRequest {

    @NotNull
    @Pattern(regexp = "NORMAL|OFFLINE|REJECT_NEXT|ACK_LOST")
    private String mode;

    @Min(0)
    private Integer settlementDelaySeconds;
}
