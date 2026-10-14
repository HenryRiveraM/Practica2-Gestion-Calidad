package com.cocacola.eventintelligence.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.eventintelligence.service.EventReportService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {
    @Mock
    private EventReportService reportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new ReportController(reportService);
        controller.access = org.mockito.Mockito.mock(com.cocacola.eventintelligence.security.AccessPolicy.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsCsvDownloadForRequestedEvent() throws Exception {
        byte[] csv = "\uFEFFSección,Indicador,Valor\r\n".getBytes(StandardCharsets.UTF_8);
        when(reportService.generateEventCsv(21L)).thenReturn(csv);

        mockMvc.perform(get("/api/reports/events/21"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv;charset=UTF-8")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"event-21-report.csv\""))
                .andExpect(content().bytes(csv));

        verify(reportService).generateEventCsv(21L);
    }
}
