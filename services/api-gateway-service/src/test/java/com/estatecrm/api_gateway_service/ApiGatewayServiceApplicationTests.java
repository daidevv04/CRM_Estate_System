package com.estatecrm.api_gateway_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "IT_INFRA", matches = "up")
class ApiGatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
