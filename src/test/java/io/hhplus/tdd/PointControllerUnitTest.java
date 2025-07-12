package io.hhplus.tdd;

import io.hhplus.tdd.point.controller.PointController;
import io.hhplus.tdd.point.model.PointHistory;
import io.hhplus.tdd.point.model.TransactionType;
import io.hhplus.tdd.point.model.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


// 컨트롤러 자체의 책임 범위를 검증
@WebMvcTest(PointController.class)
public class PointControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testPoint() throws Exception {
        // given
        long userId = 1;
        given(pointService.findUserPointById(userId)).willReturn(UserPoint.empty(userId));

        // when + then
        String urlTemplate = String.format("/point/%d", userId);
        mockMvc.perform(get(urlTemplate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(0));
    }

    @Test
    public void testHistories() throws Exception {
        // given
        long userId = 1;
        long cursor = 0;
        long amount = 1000L;
        List<PointHistory> expectedHistories = Arrays.asList(
                new PointHistory(++cursor, userId, amount, TransactionType.CHARGE, 0),
                new PointHistory(++cursor, userId, amount * 2, TransactionType.USE, 0));
        given(pointService.loadPointHistories(userId)).willReturn(expectedHistories);


        // when + then
        String urlTemplate = String.format("/point/%d/histories", userId);
        mockMvc.perform(get(urlTemplate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].type").value(TransactionType.CHARGE.name()))
                .andExpect(jsonPath("$[0].amount").value(amount))
                .andExpect(jsonPath("$[1].userId").value(userId))
                .andExpect(jsonPath("$[1].type").value(TransactionType.USE.name()))
                .andExpect(jsonPath("$[1].amount").value(amount * 2));

    }

    @Test
    public void testCharge() throws Exception {
        // given
        long userId = 1;
        long amount = 1000L;
        String urlTemplate = String.format("/point/%d/charge", userId);
        UserPoint userPoint = new UserPoint(userId, amount, System.currentTimeMillis());
        given(pointService.charge(userId, amount)).willReturn(userPoint);

        // when + then
        mockMvc.perform(patch(urlTemplate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(amount));
    }

    @Test
    public void testUse() throws Exception {
        // given
        long userId = 1;
        long amount = 1000L;
        String urlTemplate = String.format("/point/%d/use", userId); // Corrected URL template
        UserPoint userPoint = new UserPoint(userId, amount, System.currentTimeMillis());
        given(pointService.use(userId, amount)).willReturn(userPoint);

        // when + then
        mockMvc.perform(patch(urlTemplate)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(amount));
    }

}