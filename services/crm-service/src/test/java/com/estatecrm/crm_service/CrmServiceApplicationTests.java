package com.estatecrm.crm_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = IsolatedCrmDatabase.class)
@EnabledIfEnvironmentVariable(named = "IT_INFRA", matches = "up")
class CrmServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
