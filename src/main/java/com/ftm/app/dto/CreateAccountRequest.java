package com.ftm.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateAccountRequest(
    @NotBlank String ownerName,
    @NotNull @PositiveOrZero BigDecimal initialBalance,
    @NotBlank String currency
) {}
