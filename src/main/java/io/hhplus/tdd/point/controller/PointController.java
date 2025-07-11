package io.hhplus.tdd.point.controller;

import io.hhplus.tdd.point.model.PointHistory;
import io.hhplus.tdd.point.model.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);
    private final PointService pointService;

    @GetMapping("{id}")
    public UserPoint point(@PathVariable long id) {
        log.info("[GET /point/{}] 포인트 조회 요청", id);
        UserPoint userPoint = pointService.findUserPointById(id);
        log.debug("UserPoint 조회 결과: {}", userPoint);
        return userPoint;
    }

    @GetMapping("{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        log.info("[GET /point/{}/histories] 포인트 히스토리 조회 요청", id);
        List<PointHistory> histories = pointService.loadPointHistories(id);
        log.debug("조회된 히스토리 개수: {}", histories.size());
        return histories;
    }

    @PatchMapping("{id}/charge")
    public UserPoint charge(@PathVariable long id, @RequestBody long amount) {
        log.info("[PATCH /point/{}/charge] 포인트 충전 요청 - amount: {}", id, amount);
        UserPoint result = pointService.charge(id, amount);
        log.info("충전 완료 - userId: {}, 잔액: {}", result.id(), result.point());
        return result;
    }

    @PatchMapping("{id}/use")
    public UserPoint use(@PathVariable long id, @RequestBody long amount) {
        log.info("[PATCH /point/{}/use] 포인트 사용 요청 - amount: {}", id, amount);
        UserPoint result = pointService.use(id, amount);
        log.info("사용 완료 - userId: {}, 잔액: {}", result.id(), result.point());
        return result;
    }
}

