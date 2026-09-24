package com.estatecrm.config_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config tap trung cho toan bo he thong. Backend dang dung la native (doc file
 * trong config-repo/ da mount vao container), nen khong can them git repo hay
 * Vault. Doi backend chi can doi profile + them vai dong properties, khong phai
 * sua Java.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigServiceApplication.class, args);
	}

}
