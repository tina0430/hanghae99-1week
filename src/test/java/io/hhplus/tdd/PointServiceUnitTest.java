package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.error.ExceedMaxChargeAmountException;
import io.hhplus.tdd.error.InsufficientPointException;
import io.hhplus.tdd.error.InvalidAmountException;
import io.hhplus.tdd.point.model.PointHistory;
import io.hhplus.tdd.point.model.TransactionType;
import io.hhplus.tdd.point.model.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;

public class PointServiceUnitTest {

    private final long TEST_USER_ID = 0L;
    private final UserPointTable userPointTable = mock(UserPointTable.class);
    private final PointHistoryTable pointHistoryTable = mock(PointHistoryTable.class);

    private PointService pointService;

    @BeforeEach
    public void setUp() {
        pointService = new PointService(userPointTable, pointHistoryTable, 1000000L);
    }

    /**
     * 포인트 조회 테스트
     */
    @Test
    @DisplayName("포인트 조회 테스트")
    void findUserPointById_whenUserExists_returnsUserPoint() {
        // given
        long userId = TEST_USER_ID;
        UserPoint expectedUserPoint = new UserPoint(userId, 1000, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(expectedUserPoint);

        // when
        UserPoint actualUserPoint = pointService.findUserPointById(userId);

        // then
        assertThat(actualUserPoint).isEqualTo(expectedUserPoint);
    }

    @Test
    @DisplayName("존재하지 않는 유저 포인트 조회 테스트")
    void findUserPointById_whenUserDoesNotExist_returnsEmptyUserPoint() {
        // given
        long userId = 999L; // 존재하지 않는 유저 ID
        given(userPointTable.selectById(userId)).willReturn(UserPoint.empty(userId));

        // when
        UserPoint actualUserPoint = pointService.findUserPointById(userId);

        // then
        assertThat(actualUserPoint.id()).isEqualTo(userId);
        assertThat(actualUserPoint.point()).isEqualTo(0);
    }

    @Test
    @DisplayName("포인트 내역 조회 테스트")
    void loadPointHistories_whenHistoriesExist_returnsListOfHistories() {
        // given
        long userId = TEST_USER_ID;
        int expectedCount = 10;
        List<PointHistory> histories = new ArrayList<>();
        for (int i = 0; i < expectedCount; i++) {
            histories.add(new PointHistory(i, userId, 1000, TransactionType.CHARGE, System.currentTimeMillis()));
        }
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(histories);

        // when
        List<PointHistory> actualHistories = pointService.loadPointHistories(userId);

        // then
        assertThat(actualHistories).hasSize(expectedCount);

    }

    /**
     * 포인트 충전 테스트
     */
    @Test
    @DisplayName("포인트 충전 정상 케이스 테스트")
    void charge_whenAmountIsValid_updatesPointAndRecordsHistory() {
        // given
        long userId = 1L;
        long initialPoint = 500L;
        long chargeAmount = 1000L;
        UserPoint initialUserPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, initialPoint + chargeAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(initialUserPoint);
        given(userPointTable.insertOrUpdate(userId, initialPoint + chargeAmount)).willReturn(updatedUserPoint);

        // when
        UserPoint result = pointService.charge(userId, chargeAmount);

        // then
        assertThat(result.point()).isEqualTo(updatedUserPoint.point());
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트 충전 예외 케이스 테스트")
    public void charge_whenAmountIsWrong_throwsException() {
        // given
        long userId = TEST_USER_ID;
        UserPoint expect = mock(UserPoint.class);
        given(userPointTable.selectById(userId)).willReturn(expect);

        // when + then

        assertThatThrownBy(() -> pointService.charge(userId, 0)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> pointService.charge(userId, -1000)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> pointService.charge(userId, 1000001L)).isInstanceOf(InvalidAmountException.class); // maxChargeAmount 초과
    }

    @Test
    @DisplayName("누적 충전 포인트 오버플로우 테스트")
    public void charge_whenAccumulatedPointOverflows_throwsException() {
        // given
        long userId = TEST_USER_ID;
        long currentPoint = Long.MAX_VALUE - 1000; // 거의 최대치
        long chargeAmount = 2000; // 오버플로우를 유발할 금액
        UserPoint initialUserPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(initialUserPoint);
        given(userPointTable.insertOrUpdate(anyLong(), anyLong())).willThrow(new ExceedMaxChargeAmountException("포인트 값이 너무 커서 처리할 수 없습니다."));

        // when + then
        assertThatThrownBy(() -> pointService.charge(userId, chargeAmount))
                .isInstanceOf(ExceedMaxChargeAmountException.class);
    }

    /**
     * 포인트 사용 테스트
     */
    @Test
    @DisplayName("포인트 사용 정상 케이스 테스트")
    void use_whenAmountIsValid_updatesPointAndRecordsHistory() {
        // given
        long userId = 1L;
        long initialPoint = 1500L;
        long useAmount = 500L;
        UserPoint initialUserPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        UserPoint updatedUserPoint = new UserPoint(userId, initialPoint - useAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(initialUserPoint);
        given(userPointTable.insertOrUpdate(userId, initialPoint - useAmount)).willReturn(updatedUserPoint);

        // when
        UserPoint result = pointService.use(userId, useAmount);

        // then
        assertThat(result.point()).isEqualTo(updatedUserPoint.point());
        verify(pointHistoryTable, times(1)).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("사용 예외 테스트")
    public void use_whenAmountIsWrong_throwsException() {
        // given
        long userId = TEST_USER_ID;
        long point = 100L;
        UserPoint expect = new UserPoint(userId, point, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(expect);

        // when + then
        assertThatThrownBy(() -> pointService.use(userId, 0)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> pointService.use(userId, -1000)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> pointService.use(userId, point + 1)).isInstanceOf(InsufficientPointException.class);
    }

}
