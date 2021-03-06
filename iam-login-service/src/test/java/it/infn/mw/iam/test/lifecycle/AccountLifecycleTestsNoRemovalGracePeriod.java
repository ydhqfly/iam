/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare (INFN). 2016-2019
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.infn.mw.iam.test.lifecycle;


import static it.infn.mw.iam.test.api.TestSupport.EXPECTED_ACCOUNT_NOT_FOUND;
import static it.infn.mw.iam.test.api.TestSupport.TEST_USER_UUID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import it.infn.mw.iam.IamLoginService;
import it.infn.mw.iam.core.lifecycle.ExpiredAccountsHandler;
import it.infn.mw.iam.persistence.model.IamAccount;
import it.infn.mw.iam.persistence.repository.IamAccountRepository;

import it.infn.mw.iam.test.core.CoreControllerTestSupport;
import it.infn.mw.iam.test.lifecycle.AccountLifecycleTests.TestConfig;
import it.infn.mw.iam.test.lifecycle.cern.LifecycleTestSupport;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
    classes = {IamLoginService.class, CoreControllerTestSupport.class, TestConfig.class})
@WebAppConfiguration
@Transactional
@TestPropertySource(
    properties = {"lifecycle.account.expiredAccountPolicy.suspensionGracePeriodDays=0",
        "lifecycle.account.expiredAccountPolicy.removalGracePeriodDays=0"})
public class AccountLifecycleTestsNoRemovalGracePeriod implements LifecycleTestSupport {

  @Configuration
  public static class TestConfig {
    @Bean
    @Primary
    Clock mockClock() {
      return Clock.fixed(NOW, ZoneId.systemDefault());
    }
  }

  @Autowired
  private IamAccountRepository repo;

  @Autowired
  private ExpiredAccountsHandler handler;

  @Test
  public void testZeroDaysRemovalGracePeriod() {
    IamAccount testAccount =
        repo.findByUuid(TEST_USER_UUID).orElseThrow(assertionError(EXPECTED_ACCOUNT_NOT_FOUND));

    assertThat(testAccount.isActive(), is(true));

    testAccount.setEndTime(Date.from(FOUR_DAYS_AGO));
    repo.save(testAccount);

    handler.run();

    Optional<IamAccount> deletedTestAccount = repo.findByUuid(TEST_USER_UUID);

    assertThat(deletedTestAccount.isPresent(), is(false));
  }

}
