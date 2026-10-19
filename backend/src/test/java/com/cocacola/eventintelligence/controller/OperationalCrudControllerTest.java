package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cocacola.eventintelligence.entity.*;
import com.cocacola.eventintelligence.service.*;
import com.cocacola.eventintelligence.security.AccessPolicy;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationalCrudControllerTest {
    private AccessPolicy access;
    @BeforeEach void setup(){access=mock(AccessPolicy.class);}
    @Test void activityListFiltersUnreadableEntries(){var service=mock(ActivityService.class);var c=new ActivityController(service);c.access=access;var visible=new Activity();var hidden=new Activity();when(service.findAll()).thenReturn(List.of(visible,hidden));when(access.canRead(visible)).thenReturn(true);assertEquals(List.of(visible),c.findAll());}
    @Test void activityDetailChecksRead(){var service=mock(ActivityService.class);var c=new ActivityController(service);c.access=access;var a=new Activity();when(service.findById(7L)).thenReturn(a);assertSame(a,c.findById(7L));verify(access).requireRead(a);}
    @Test void activityRecordChecksEventAndParticipant(){var service=mock(ActivityService.class);var c=new ActivityController(service);c.access=access;var request=mock(com.cocacola.eventintelligence.dto.ActivityRequest.class);when(request.eventId()).thenReturn(7L);when(request.participantId()).thenReturn(2L);var a=new Activity();when(service.record(request)).thenReturn(a);assertSame(a,c.record(request));var order=inOrder(access,service);order.verify(access).requireOperate(7L);order.verify(access).requireParticipantAtEvent(7L,2L);order.verify(service).record(request);}
    @Test void activityUpdateChecksBothVersions(){var service=mock(ActivityService.class);var c=new ActivityController(service);c.access=access;var old=new Activity();var next=new Activity();when(service.findById(7L)).thenReturn(old);when(service.update(7L,next)).thenReturn(next);assertSame(next,c.update(7L,next));verify(access).requireWrite(old);verify(access).requireWrite(next);}
    @Test void activityDeleteChecksWrite(){var service=mock(ActivityService.class);var c=new ActivityController(service);c.access=access;var a=new Activity();when(service.findById(7L)).thenReturn(a);c.delete(7L);var order=inOrder(access,service);order.verify(service).findById(7L);order.verify(access).requireWrite(a);order.verify(service).delete(7L);}
    @Test void surveyListFiltersUnreadableEntries(){var service=mock(SurveyService.class);var c=new SurveyController(service);c.access=access;var visible=new Survey();var hidden=new Survey();when(service.findAll()).thenReturn(List.of(visible,hidden));when(access.canRead(visible)).thenReturn(true);assertEquals(List.of(visible),c.findAll());}
    @Test void surveyDetailChecksRead(){var service=mock(SurveyService.class);var c=new SurveyController(service);c.access=access;var a=new Survey();when(service.findById(7L)).thenReturn(a);assertSame(a,c.findById(7L));verify(access).requireRead(a);}
    @Test void surveyUpdateChecksBothVersions(){var service=mock(SurveyService.class);var c=new SurveyController(service);c.access=access;var old=new Survey();var next=new Survey();when(service.findById(7L)).thenReturn(old);when(service.update(7L,next)).thenReturn(next);assertSame(next,c.update(7L,next));verify(access).requireWrite(old);verify(access).requireWrite(next);}
    @Test void surveyDeleteChecksWrite(){var service=mock(SurveyService.class);var c=new SurveyController(service);c.access=access;var a=new Survey();when(service.findById(7L)).thenReturn(a);c.delete(7L);var order=inOrder(access,service);order.verify(service).findById(7L);order.verify(access).requireWrite(a);order.verify(service).delete(7L);}
}
