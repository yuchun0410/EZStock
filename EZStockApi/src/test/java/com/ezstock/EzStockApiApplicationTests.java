package com.ezstock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"ezstock.import.run-on-startup=false", "ezstock.import.us-stock-list=false"})
class EzStockApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
