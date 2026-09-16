package com.estatecrm.crm_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IT_INFRA", matches = "up")
class CrmServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
