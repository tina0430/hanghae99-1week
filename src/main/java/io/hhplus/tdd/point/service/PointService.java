package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.error.ExceedMaxChargeAmountException;
import io.hhplus.tdd.error.InsufficientPointException;
import io.hhplus.tdd.error.InvalidAmountException;
import io.hhplus.tdd.point.model.PointHistory;
import io.hhplus.tdd.point.model.TransactionType;
import io.hhplus.tdd.point.model.UserPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 예외를 던지는 책임은 “비즈니스 해석”
 */
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final long maxChargeAmount;

    public PointService(
            UserPointTable userPointTable,
            PointHistoryTable pointHistoryTable,
            @Value("${point.max-charge-amount}") long maxChargeAmount
    ) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
        this.maxChargeAmount = maxChargeAmount;
    }

    public UserPoint findUserPointById(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> loadPointHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    public UserPoint charge(long userId, long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("유효하지 않은 포인트 충전 시도: " + amount);
        } else if (amount > maxChargeAmount) {
            throw new InvalidAmountException("1회 충전 한도 초과: " + amount);
        }
        UserPoint userPoint = updatePoint(userId, amount);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis()); // todo 따로 검증
        return userPoint;
    }

    public UserPoint use(long userId, long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException("유효하지 않은 포인트 사용 시도: " + amount);
        }
        UserPoint userPoint = updatePoint(userId, amount * -1);
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
        return userPoint;
    }

    private UserPoint updatePoint(long userid, long delta) {
        ReentrantLock lock = locks.computeIfAbsent(userid, id -> new ReentrantLock());
        lock.lock();
        try {
            long currentPoint = userPointTable.selectById(userid).point();
            try {
                long updatedPoint = Math.addExact(currentPoint, delta);
                if (updatedPoint < 0) {
                    throw new InsufficientPointException("포인트가 부족합니다.");
                }
                return userPointTable.insertOrUpdate(userid, updatedPoint);
            } catch (ArithmeticException e) {
                // 입력된 값이 너무 큰건지, 이미 있던 포인트가 거의 한계치에 다다른건지 알 수 없음
                // 포인트가 넘치면 UserPoint를 여러개 가져야 하나?
                throw new ExceedMaxChargeAmountException("포인트 값이 너무 커서 처리할 수 없습니다.");
            }
        } finally {
            lock.unlock();
        }
    }

}
