package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.dto.ActivityRequest;
import com.cocacola.eventintelligence.entity.Activity;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.repository.ActivityRepository;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ParticipantRepository participantRepository;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, eventRepository, participantRepository);
    }

    @Test
    void shouldPersistValidActivityAgainstExistingEventAndParticipant() {
        Event event = new Event();
        event.setId(10L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(participantRepository.findById(20L))
                .thenReturn(Optional.of(new com.cocacola.eventintelligence.entity.Participant()));
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-10T14:30:00Z");
        ActivityRequest request = new ActivityRequest(10L, 20L, "SAMPLING", 12, "12 muestras entregadas",
                occurredAt);

        Activity saved = activityService.record(request);

        ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(activityCaptor.capture());
        assertEquals(10L, saved.getEventId());
        assertEquals(20L, saved.getParticipantId());
        assertEquals("SAMPLING", saved.getType());
        assertEquals(12, saved.getQuantity());
        assertEquals("12 muestras entregadas", saved.getResult());
        assertEquals(occurredAt, saved.getStartAt());
        assertEquals("COMPLETED", saved.getStatus());
        assertEquals(saved, activityCaptor.getValue());
    }
}
