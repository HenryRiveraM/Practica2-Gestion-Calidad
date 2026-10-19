package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cocacola.eventintelligence.security.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class AuthControllerTest {
    private AccountStore accounts;private AccountManagement management;private AccessPolicy access;
    private RequestThrottle throttle;private AuthController controller;private MockHttpServletRequest request;
    private Account account;
    @BeforeEach void setup(){
        accounts=mock(AccountStore.class);management=mock(AccountManagement.class);access=mock(AccessPolicy.class);throttle=mock(RequestThrottle.class);
        controller=new AuthController(accounts,management,access,throttle);request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        account=new Account(1L,"Admin","admin@example.com","hash",true,0,Set.of(Role.ADMINISTRADOR),Set.of());
    }
    @Test void exposesCsrfTokenAndHeader(){assertEquals(Map.of("token","abc","headerName","X-CSRF"),controller.csrf(new DefaultCsrfToken("X-CSRF","csrf","abc")));}
    @Test void setupStatusReflectsEmptyStore(){when(accounts.isEmpty()).thenReturn(true);assertEquals(Map.of("setupRequired",true),controller.status());}
    @Test void meUsesCurrentAccountAndRole(){when(access.account()).thenReturn(account);when(access.role()).thenReturn(Role.ADMINISTRADOR);var view=Map.<String,Object>of("name","Admin");when(management.view(account,Role.ADMINISTRADOR)).thenReturn(view);assertSame(view,controller.me());}
    @Test void setupThrottlesBeforeCreatingAccount(){var body=new AuthController.Setup("code","Name","a@example.com","long-password");assertEquals(Map.of("created",true),controller.setup(body,request));var order=inOrder(throttle,management);order.verify(throttle).check("setup:127.0.0.1",10);order.verify(management).setup("code","Name","a@example.com","long-password");}
    @Test void activationThrottlesBeforeChangingPassword(){assertEquals(Map.of("activated",true),controller.activate(new AuthController.Activation("token","long-password"),request));var order=inOrder(throttle,management);order.verify(throttle).check("activate:127.0.0.1",20);order.verify(management).activate("token","long-password");}
    @Test void switchesOnlyToAssignedRoleAndStoresItInSession(){when(access.account()).thenReturn(account);var view=Map.<String,Object>of("role","ADMINISTRADOR");when(management.view(account,Role.ADMINISTRADOR)).thenReturn(view);assertSame(view,controller.switchRole(new AuthController.SwitchRole(Role.ADMINISTRADOR),request));assertEquals(Role.ADMINISTRADOR,request.getSession().getAttribute(SessionAccountFilter.ACTIVE_ROLE));}
    @Test void deniedRoleDoesNotCreateSessionOrChangeAccount(){when(access.account()).thenReturn(account);assertThrows(AccessDeniedException.class,()->controller.switchRole(new AuthController.SwitchRole(Role.MARKETING),request));assertNull(request.getSession(false));verifyNoInteractions(management);}
}
