package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.cocacola.eventintelligence.security.RequestThrottle;
import com.cocacola.eventintelligence.service.RegistrationService;
import com.cocacola.eventintelligence.dto.*;
import com.cocacola.eventintelligence.exception.DuplicateRegistrationException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class PublicEventControllerTest {
    private JdbcTemplate jdbc;private RegistrationService registrations;private RequestThrottle throttle;private PublicEventController controller;
    @BeforeEach void setup(){jdbc=mock(JdbcTemplate.class);registrations=mock(RegistrationService.class);throttle=mock(RequestThrottle.class);controller=new PublicEventController(jdbc,registrations,throttle);}
    @Test void listsAvailableEvents(){var rows=List.of(Map.<String,Object>of("id",7L));when(jdbc.queryForList(anyString())).thenReturn(rows);assertSame(rows,controller.availableEvents());}
    @Test void returnsPublishedEvent(){var row=Map.<String,Object>of("id",7L);when(jdbc.queryForList(anyString(),eq("token"))).thenReturn(List.of(row));assertSame(row,controller.event("token"));}
    @Test void missingPublicLinkReturns404(){assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.event("missing")).getStatusCode().value());}
    @Test void registersAgainstEventResolvedFromToken(){
        when(jdbc.queryForList(anyString(),eq("token"))).thenReturn(List.of(Map.of("id",7L)));
        when(registrations.register(any())).thenReturn(new RegistrationResponse(1L,7L,"Event",2L,"Ana Perez","qr"));
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        var body=new PublicEventController.PublicRegistration(" Ana "," Perez ","ana@example.com",null,"La Paz","18-24","Sprite",true);
        assertEquals(Map.of("eventName","Event","participantName","Ana Perez","qrToken","qr"),controller.register("token",body,request));
        var captor=org.mockito.ArgumentCaptor.forClass(RegistrationRequest.class);verify(registrations).register(captor.capture());
        assertEquals(7L,captor.getValue().eventId());assertEquals("La Paz",captor.getValue().city());assertTrue(captor.getValue().communicationConsent());
        verify(throttle).check("registration:127.0.0.1",40);
    }
    @Test void translatesDuplicateInto409(){
        when(jdbc.queryForList(anyString(),eq("token"))).thenReturn(List.of(Map.of("id",7L)));
        when(registrations.register(any())).thenThrow(new DuplicateRegistrationException(7L,2L));
        var body=new PublicEventController.PublicRegistration("Ana","Perez","ana@example.com",null,null,null,null,false);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.register("token",body,new MockHttpServletRequest())).getStatusCode().value());
    }
    @Test void invalidLinkDoesNotRegisterParticipant(){var body=new PublicEventController.PublicRegistration("Ana","Perez","ana@example.com",null,null,null,null,false);assertThrows(ResponseStatusException.class,()->controller.register("missing",body,new MockHttpServletRequest()));verifyNoInteractions(registrations);}
}
