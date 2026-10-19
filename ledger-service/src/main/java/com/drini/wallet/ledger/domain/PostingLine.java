package com.drini.wallet.ledger.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of a posting that is about to be written: which account, DEBIT or CREDIT, how much. */
public class PostingLine {

    private final UUID accountId;
    private final Direction direction;
    private final BigDecimal amount;

    public PostingLine(UUID accountId, Direction direction, BigDecimal amount) {
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
    }

    public static PostingLine debit(UUID accountId, BigDecimal amount) {
        return new PostingLine(accountId, Direction.DEBIT, amount);
    }

    public static PostingLine credit(UUID accountId, BigDecimal amount) {
        return new PostingLine(accountId, Direction.CREDIT, amount);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
