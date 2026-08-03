package com.company.dss.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DssSignatureUtilsTest {

  @Test
  void computeSignature_matchesDssV8Formula() {
    String username = "system";
    String password = "admin1234567";
    String realm = "f689ed8c40030d68007d6e95002f7f87";
    String randomKey = "48c008d0026a49c4";

    String temp1 = DssSignatureUtils.md5(password);
    String temp2 = DssSignatureUtils.md5(username + temp1);
    String temp3 = DssSignatureUtils.md5(temp2);
    String temp4 = DssSignatureUtils.md5(username + ":" + realm + ":" + temp3);
    String expected = DssSignatureUtils.md5(temp4 + ":" + randomKey);

    assertThat(DssSignatureUtils.computeSignature(username, password, realm, randomKey))
        .isEqualTo(expected)
        .hasSize(32);
  }

  @Test
  void md5_producesLowercaseHex() {
    assertThat(DssSignatureUtils.md5("test")).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
  }
}
