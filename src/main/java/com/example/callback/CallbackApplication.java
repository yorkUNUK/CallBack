package com.example.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication()
public class CallbackApplication {

	public static void main(String[] args) {
		Logger logger = LoggerFactory.getLogger(CallbackApplication.class);
		SpringApplication.run(CallbackApplication.class, args);
		logger.info("----------------启动成功----------------");
	}
}
