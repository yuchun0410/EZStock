package com.ezstock.dao;

import com.ezstock.dao.jpa.StockJpaDao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"ezstock.dao.type=jpa", "ezstock.import.run-on-startup=false"})
class JpaDaoTest extends AbstractDaoTest {

    @Test
    void usesJpaImplementation() {
        assertThat(stockDao).isInstanceOf(StockJpaDao.class);
    }
}
