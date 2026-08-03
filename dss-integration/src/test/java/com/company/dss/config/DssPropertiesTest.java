package com.company.dss.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DssPropertiesTest {

  @Test
  void getBaseUrl_buildsFromProtocolHostPort() {
    DssProperties properties = new DssProperties();
    properties.setProtocol("https");
    properties.setHost("192.168.1.147");
    properties.setPort(443);

    assertThat(properties.getBaseUrl()).isEqualTo("https://192.168.1.147:443");
  }

  @Test
  void validateForLogin_failsWhenHostMissing() {
    DssProperties properties = new DssProperties();
    properties.setUsername("system");
    properties.setPassword("secret");

    assertThatThrownBy(properties::validateForLogin)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DSS_HOST");
  }

  @Test
  void isConfigured_falseWhenIncomplete() {
    DssProperties properties = new DssProperties();
    assertThat(properties.isConfigured()).isFalse();
  }
}
