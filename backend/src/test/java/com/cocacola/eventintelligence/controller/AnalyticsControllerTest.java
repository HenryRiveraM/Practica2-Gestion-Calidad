package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.cocacola.eventintelligence.security.*;
import com.cocacola.eventintelligence.service.*;
import com.cocacola.eventintelligence.exception.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsControllerTest {
    private JdbcTemplate jdbc;
    private AnalyticsOverviewService overview;
    private EventImpactScoreService score;
    private AccessPolicy access;
    private AnalyticsController controller;

    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class); overview=mock(AnalyticsOverviewService.class);
        score=mock(EventImpactScoreService.class); access=mock(AccessPolicy.class);
        controller=new AnalyticsController(jdbc,overview,score); controller.access=access;
    }
    @Test void rejectsReversedDateRangeBeforeQuerying() {
        assertThrows(BusinessRuleException.class,()->controller.overview(null,null,LocalDate.of(2026,10,19),LocalDate.of(2026,10,1)));
        verifyNoInteractions(access,overview,jdbc);
    }
    @Test void overviewWithoutFiltersUsesAccountScope() {
        when(access.eventScope()).thenReturn(Set.of(7L));
        var result=Map.<String,Object>of("registered",12);
        when(overview.getScopedOverview(null,null,null,null,Set.of(7L))).thenReturn(result);
        assertSame(result,controller.overview(null,null,null,null));
        verify(access,never()).requireEvent(any());
    }
    @Test void overviewWithStartOnlyIsValid() {
        var start=LocalDate.of(2026,10,1);
        controller.overview(null,null,start,null);
        verify(overview).getScopedOverview(null,null,start,null,Set.of());
    }
    @Test void overviewWithEndOnlyIsValid() {
        var end=LocalDate.of(2026,10,19);
        controller.overview(null,null,null,end);
        verify(overview).getScopedOverview(null,null,null,end,Set.of());
    }
    @Test void overviewWithSameDateAndEventChecksAccess() {
        var date=LocalDate.of(2026,10,19);
        controller.overview(7L,2L,date,date);
        var order=inOrder(access,overview);
        order.verify(access).requireEvent(7L);
        order.verify(access).eventScope();
        order.verify(overview).getScopedOverview(7L,2L,date,date,Set.of());
    }
    @Test void deniesOverviewBeforeLoadingMetrics() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(access).requireEvent(7L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.overview(7L,null,null,null));
        verifyNoInteractions(overview);
    }
    @ParameterizedTest @ValueSource(strings={"unrestricted","empty","assigned"})
    void eventListAppliesEventScope(String scope) {
        if(scope.equals("unrestricted"))when(access.eventScope()).thenReturn(null);
        if(scope.equals("empty"))when(access.eventScope()).thenReturn(Set.of());
        if(scope.equals("assigned"))when(access.eventScope()).thenReturn(Set.of(7L));
        var rows=List.of(Map.<String,Object>of("event_id",7L));
        when(jdbc.queryForList(anyString())).thenReturn(rows);
        assertSame(rows,controller.eventPerformance());
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(jdbc).queryForList(sql.capture());
        assertTrue(sql.getValue().contains(scope.equals("empty")?" WHERE 1=0":scope.equals("assigned")?" WHERE event_id IN (7)":"FROM vw_event_performance ORDER BY"));
    }
    @Test void eventDetailReturnsFirstResultAfterAccessCheck() {
        var row=Map.<String,Object>of("event_id",7L);
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(row));
        assertSame(row,controller.eventPerformanceById(7L));verify(access).requireEvent(7L);
    }
    @Test void eventDetailRejectsMissingEvent() {
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class,()->controller.eventPerformanceById(7L));
    }
    @Test void impactScoreDelegatesAfterCheckingEvent() {
        var result=Map.<String,Object>of("score",80);when(score.getImpactScore(7L)).thenReturn(result);
        assertSame(result,controller.eventImpactScore(7L));verify(access).requireEvent(7L);
    }
    @ParameterizedTest @ValueSource(strings={"campaigns","activities","products","loyalty"})
    void analysisEndpointsReturnQueryResults(String endpoint) {
        var rows=List.of(Map.<String,Object>of("result",4));when(jdbc.queryForList(anyString())).thenReturn(rows);
        var actual=switch(endpoint) {case "campaigns"->controller.campaignPerformance();case "activities"->controller.activityAnalysis();case "products"->controller.productAnalysis();default->controller.loyaltySummary();};
        assertSame(rows,actual);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(jdbc).queryForList(sql.capture());
        assertTrue(sql.getValue().contains(switch(endpoint){case "campaigns"->"vw_campaign_performance";case "activities"->"vw_activity_analysis";case "products"->"vw_product_analysis";default->"GROUP BY loyalty_status";}));
    }
    @ParameterizedTest @ValueSource(strings={"MARKETING","ADMINISTRADOR"})
    void participantQueryAppliesConsentForMarketingOnly(String role) {
        when(access.role()).thenReturn(Role.valueOf(role));
        var rows=List.of(Map.<String,Object>of("participant_id",5));when(jdbc.queryForList(anyString())).thenReturn(rows);
        assertSame(rows,controller.participantAnalysis());
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(jdbc).queryForList(sql.capture());
        assertEquals(role.equals("MARKETING"),sql.getValue().contains("WHERE communication_consent=TRUE"));
    }
}
