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
import com.company.dss.authentication.DssSessionPersistence;
import com.company.dss.authentication.TokenHolder;
import com.company.dss.config.DssProperties;
import com.company.dss.dto.authentication.AuthLoginResponse;
import com.company.dss.dto.authentication.LoginTestResponse;
import com.company.dss.mq.MqConnectionService;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

  @Mock
  private AuthenticationClient authenticationClient;

  @Mock
  private TokenHolder tokenHolder;

  @Mock
  private DssSessionPersistence sessionPersistence;

  @Mock
  private MqConnectionService mqConnectionService;

  @Mock
  private DssProperties dssProperties;

  @InjectMocks
  private AuthenticationServiceImpl authenticationService;

  @Test
  void testLogin_returnsConnectedResponseWithToken() {
    when(authenticationClient.loginWithRecovery(true)).thenReturn(new AuthLoginResponse(
        "abc-token", 30, null, null, "1", "system", "1", null, null));

    LoginTestResponse response = authenticationService.testLogin();

    assertThat(response.connected()).isTrue();
    assertThat(response.token()).isEqualTo("abc-token");
    verify(authenticationClient).loginWithRecovery(true);
  }

  @Test
  void isConnected_delegatesToTokenHolder() {
    when(tokenHolder.hasValidToken()).thenReturn(true);
    assertThat(authenticationService.isConnected()).isTrue();
  }
}
