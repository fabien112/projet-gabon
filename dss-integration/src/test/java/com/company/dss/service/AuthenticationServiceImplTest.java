package com.company.dss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.company.dss.authentication.AuthenticationClient;
import com.company.dss.authentication.TokenHolder;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

  @Mock
  private AuthenticationClient authenticationClient;

  @Mock
  private TokenHolder tokenHolder;

  @InjectMocks
  private AuthenticationServiceImpl authenticationService;

  @Test
  void testLogin_returnsConnectedResponseWithToken() {
    when(authenticationClient.login()).thenReturn(new AuthLoginResponse(
        "abc-token", 30, null, null, "1", "system", null, null));

    LoginTestResponse response = authenticationService.testLogin();

    assertThat(response.connected()).isTrue();
    assertThat(response.token()).isEqualTo("abc-token");
    verify(authenticationClient).login();
  }

  @Test
  void isConnected_delegatesToTokenHolder() {
    when(tokenHolder.hasValidToken()).thenReturn(true);
    assertThat(authenticationService.isConnected()).isTrue();
  }
}
