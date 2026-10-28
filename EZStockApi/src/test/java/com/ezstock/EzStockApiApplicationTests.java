package com.ezstock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "ezstock.import.run-on-startup=false")
class EzStockApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
