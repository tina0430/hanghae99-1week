package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.error.InsufficientPointException;
import io.hhplus.tdd.point.model.PointHistory;
import io.hhplus.tdd.point.model.TransactionType;
import io.hhplus.tdd.point.model.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest()
public class PointServiceIntegrationTest {

    static final AtomicLong userIdGenerator = new AtomicLong();

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointHistoryTable pointHistoryTable;

    @Autowired
    private PointService pointService;

    /**
     * 서비스 기본 동작 테스트
     */
    @Test
    @DisplayName("내역 조회 테스트")
    void loadPointHistories() throws Exception {
        // given
        long userId = userIdGenerator.incrementAndGet();
        int expectedCount = 10;
        List<PointHistory> histories = new ArrayList<>();
        for (int i = 0; i < expectedCount * 2; i++) {
            if (i % 2 == 0) {
                pointService.charge(userId, 1000);
                histories.add(new PointHistory(i, userId, 1000, TransactionType.CHARGE, System.currentTimeMillis()));
            } else {
                pointService.charge(userIdGenerator.incrementAndGet(), 1000);
            }
        }

        // when
        List<PointHistory> actualHistories = pointService.loadPointHistories(userId);

        // then
        Assertions.assertThat(actualHistories).hasSize(expectedCount);
    }

    @Test
    @DisplayName("포인트 충전 후 사용 시나리오 테스트")
    void chargeAndUse_whenScenario_thenFinalBalanceIsCorrect() {
        // given
        long userId = userIdGenerator.incrementAndGet();
        long chargeAmount = 5000L;
        long useAmount = 2000L;

        // when
        pointService.charge(userId, chargeAmount);
        pointService.use(userId, useAmount);

        // then
        UserPoint userPoint = userPointTable.selectById(userId);
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(userPoint.point()).isEqualTo(chargeAmount - useAmount);
        softly.assertThat(histories).hasSize(2);
        softly.assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE);
        softly.assertThat(histories.get(1).type()).isEqualTo(TransactionType.USE);
        softly.assertAll();
    }

    @Test
    @DisplayName("잔액 부족 시나리오 테스트")
    void use_whenInsufficientBalance_thenThrowsExceptionAndBalanceUnchanged() {
        // given
        long userId = userIdGenerator.incrementAndGet();
        long initialAmount = 1000L;
        long useAmount = 2000L;
        pointService.charge(userId, initialAmount);

        // when & then
        assertThatThrownBy(() -> {pointService.use(userId, useAmount);}).isInstanceOf(InsufficientPointException.class);

        UserPoint userPoint = userPointTable.selectById(userId);
        List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(userPoint.point()).isEqualTo(initialAmount);
        softly.assertThat(histories).hasSize(1); // 충전 내역만 있어야 함
        softly.assertAll();
    }

    /**
     * 동시성 제어 테스트
     */
    @Test
    @DisplayName("유저 한명, 다중 충전 테스트")
    void concurrentCharge_whenMultipleThreads_thenSumIsCorrect() throws Exception {
        // given
        long userId = userIdGenerator.incrementAndGet();
        int threadCount = 10;
        long chargeAmount = 1000L;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.charge(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        UserPoint userPoint = userPointTable.selectById(userId);
        List<PointHistory> pointHistories = pointHistoryTable.selectAllByUserId(userId);
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(userPoint.point()).isEqualTo(chargeAmount * threadCount);
        softly.assertThat(pointHistories).hasSize(threadCount);

        softly.assertAll();
    }

    @Test
    @DisplayName("유저 한명, 다중 사용 테스트")
    void concurrentUse_whenMultipleThreads_thenBalanceIsCorrect() throws Exception {
        // given
        long userId = userIdGenerator.incrementAndGet();
        int threadCount = 10;
        int chargeCount = 5;
        long amount = 1000L;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        AtomicInteger insufficientCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        pointService.charge(userId, amount * chargeCount);
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.use(userId, amount);
                } catch (InsufficientPointException e) {
                    insufficientCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        UserPoint userPoint = userPointTable.selectById(userId);
        List<PointHistory> pointHistories = pointHistoryTable.selectAllByUserId(userId);
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(userPoint.point()).isEqualTo(0L);
        softly.assertThat(pointHistories).hasSize(chargeCount + 1);
        softly.assertThat(insufficientCount.get()).isEqualTo(threadCount - chargeCount);

        softly.assertAll(); // 모든 검증을 한 번에 평가
    }

    @Test
    @DisplayName("유저 한명, 다중 충전/사용 테스트")
    void concurrentChargeAndUse_whenSimultaneous_thenFinalBalanceIsConsistent() throws Exception {
        long userId = userIdGenerator.incrementAndGet();

        // given
        int threadCount = 20;
        long amount = 1000L;
        long initialPoint = threadCount * amount;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        pointService.charge(userId, initialPoint);

        for (int i = 0; i < threadCount; i++) {
            if (i % 2 == 0) {
                executor.execute(() -> {
                    try {
                        barrier.await(); // 한꺼번에 시작
                        pointService.charge(userId, amount);
                    } catch (BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            } else {
                executor.execute(() -> {
                    try {
                        barrier.await(); // 한꺼번에 시작
                        pointService.use(userId, amount);
                    } catch (BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        latch.await();

        UserPoint userPoint = userPointTable.selectById(userId);

        // then
        assertThat(userPoint.point()).isEqualTo(initialPoint);
    }

    @Test
    @DisplayName("다중 유저, 다중 사용 테스트")
    void concurrentUse_whenMultipleUsers_thenEachUserIsolatedAndCorrect() throws Exception {
        // given
        int userCount = 5;
        int useCount = 5;
        long useAmount = 1000L;
        ExecutorService executor = Executors.newFixedThreadPool(userCount * useCount);
        CountDownLatch latch = new CountDownLatch(userCount * useCount);
        long initialAmount = useAmount * useCount;
        List<Long> userIds = new ArrayList<>();
        // 사전 충전
        for (int i = 0; i < userCount; i++) {
            long userId = userIdGenerator.incrementAndGet();
            pointService.charge(userId, initialAmount);
            userIds.add(userId);
        }

        // when
        for (Long userId : userIds) {
            for (int i = 0; i < useCount; i++) {
                executor.submit(() -> {
                    try {
                        pointService.use(userId, useAmount);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        latch.await();

        // then
        SoftAssertions softly = new SoftAssertions();
        for (Long userId : userIds) {
            UserPoint userPoint = userPointTable.selectById(userId);
            List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);

            softly.assertThat(userPoint.point())
                    .as("유저 %d 잔액".formatted(userId))
                    .isEqualTo(0L);

            softly.assertThat(histories.size())
                    .as("유저 %d 거래 성공 내역 개수".formatted(userId))
                    .isEqualTo(useCount + 1); // 사전 충전까지 포함
        }
        softly.assertAll();
    }
}
