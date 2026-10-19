package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cocacola.eventintelligence.entity.*;
import com.cocacola.eventintelligence.service.*;
import com.cocacola.eventintelligence.security.AccessPolicy;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CrudControllerTest {
    private EventService service;private AccessPolicy access;private InsightService insights;private EventController controller;private Event entity;
    @BeforeEach void setup(){service=mock(EventService.class);access=mock(AccessPolicy.class);insights=mock(InsightService.class);controller=new EventController(service,insights);controller.access=access;entity=new Event();entity.setId(7L);}
    @Test void listFiltersUnreadableEntities(){var hidden=new Event();when(service.findAll()).thenReturn(List.of(entity,hidden));when(access.canRead(entity)).thenReturn(true);assertEquals(List.of(entity),controller.findAll());}
    @Test void detailChecksReadPermission(){when(service.findById(7L)).thenReturn(entity);assertSame(entity,controller.findById(7L));verify(access).requireRead(entity);}
    @Test void createChecksWriteBeforeSaving(){when(service.create(entity)).thenReturn(entity);assertSame(entity,controller.create(entity));var order=inOrder(access,service);order.verify(access).requireWrite(entity);order.verify(service).create(entity);}
    @Test void updateChecksOldAndNewEntities(){var replacement=new Event();when(service.findById(7L)).thenReturn(entity);when(service.update(7L,replacement)).thenReturn(replacement);assertSame(replacement,controller.update(7L,replacement));var order=inOrder(access,service);order.verify(service).findById(7L);order.verify(access).requireWrite(entity);order.verify(access).requireWrite(replacement);order.verify(service).update(7L,replacement);}
    @Test void deleteChecksPermissionBeforeDeleting(){when(service.findById(7L)).thenReturn(entity);controller.delete(7L);var order=inOrder(access,service);order.verify(service).findById(7L);order.verify(access).requireWrite(entity);order.verify(service).delete(7L);}
    @Test void deniedWriteNeverSaves(){doThrow(new AccessDeniedException("denied")).when(access).requireWrite(entity);assertThrows(AccessDeniedException.class,()->controller.create(entity));verifyNoInteractions(service);}
    @Test void insightsChecksReportsAndEventBeforeProvider(){var result=Map.<String,Object>of("summary","ok");when(insights.getEventInsights(7L)).thenReturn(result);assertSame(result,controller.insights(7L));var order=inOrder(access,insights);order.verify(access).requireReports();order.verify(access).requireEvent(7L);order.verify(insights).getEventInsights(7L);}
}
