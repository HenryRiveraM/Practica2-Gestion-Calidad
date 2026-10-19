package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.cocacola.eventintelligence.security.*;
import com.cocacola.eventintelligence.service.EventService;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

class EventAccessControllerTest {
    private JdbcTemplate jdbc;private AccessPolicy access;private EventService events;
    private AccountStore accounts;private EventAccessController controller;private Event event;
    @BeforeEach void setup(){
        jdbc=mock(JdbcTemplate.class);access=mock(AccessPolicy.class);events=mock(EventService.class);accounts=mock(AccountStore.class);
        controller=new EventAccessController(jdbc,access,events,accounts);event=new Event();event.setId(7L);event.setStatus("ACTIVE");
        when(events.findById(7L)).thenReturn(event);
    }
    @Test void noPublicConfigurationReturnsDisabled(){
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of());
        var result=controller.get(7L);assertEquals(false,result.get("enabled"));assertEquals("ACTIVE",result.get("eventStatus"));
    }
    @ParameterizedTest @CsvSource({"ACTIVE,true,true","PLANNED,false,false","COMPLETED,true,false","CANCELLED,true,false"})
    void publicRegistrationRequiresEligibleStatusAndEnabledFlag(String status,boolean configured,boolean expected){
        event.setStatus(status);when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("enabled",configured,"token","public-token")));
        var result=controller.get(7L);assertEquals(expected,result.get("enabled"));assertEquals("public-token",result.get("token"));verify(access).requireAdmin();
    }
    @ParameterizedTest @CsvSource({"COMPLETED","CANCELLED"})
    void refusesClosedEventWithoutExplicitReactivation(String status){
        event.setStatus(status);
        assertThrows(BusinessRuleException.class,()->controller.set(7L,new EventAccessController.PublicAccess(true,false)));
        verify(events,never()).update(any(),any());verifyNoInteractions(accounts);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void reactivatesClosedEventAndAuditsBeforePublishing(){
        event.setStatus("COMPLETED");when(access.account()).thenReturn(new Account(1L,"Admin","admin@example.com","hash",true,0,Set.of(Role.ADMINISTRADOR),Set.of()));
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("enabled",true)));
        var result=controller.set(7L,new EventAccessController.PublicAccess(true,true));
        assertEquals("ACTIVE",event.getStatus());assertEquals(true,result.get("enabled"));verify(events).update(7L,event);
        var order=inOrder(accounts);order.verify(accounts).audit(1L,"EVENT_REACTIVATED",7L);order.verify(accounts).audit(1L,"PUBLIC_REGISTRATION_ENABLED",7L);
    }
    @Test void disablesClosedEventWithoutReactivating(){
        event.setStatus("CANCELLED");when(access.account()).thenReturn(new Account(1L,"Admin","admin@example.com","hash",true,0,Set.of(Role.ADMINISTRADOR),Set.of()));
        var result=controller.set(7L,new EventAccessController.PublicAccess(false,false));
        assertEquals(false,result.get("enabled"));assertEquals("CANCELLED",event.getStatus());verify(events,never()).update(any(),any());verify(accounts).audit(1L,"PUBLIC_REGISTRATION_DISABLED",7L);
    }
    @Test void publishesActiveEventWithoutReactivation(){
        when(access.account()).thenReturn(new Account(1L,"Admin","admin@example.com","hash",true,0,Set.of(Role.ADMINISTRADOR),Set.of()));
        when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("enabled",true)));
        assertEquals(true,controller.set(7L,new EventAccessController.PublicAccess(true,false)).get("enabled"));
        verify(events,never()).update(any(),any());verify(accounts).audit(1L,"PUBLIC_REGISTRATION_ENABLED",7L);
    }
}
