package io.hhplus.tdd.point.model;

// 여기에 싪패건을 쌓아도 되나?
public record PointHistory(
        long id,
        long userId,
        long amount,
        TransactionType type,
        long updateMillis
) {
}
