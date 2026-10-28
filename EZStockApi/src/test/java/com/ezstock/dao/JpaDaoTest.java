package com.ezstock.dao;

import com.ezstock.dao.jpa.StockJpaDao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ezstock.dao.type=jpa")
@Transactional
class JpaDaoTest extends AbstractDaoTest {

    @Test
    void usesJpaImplementation() {
        assertThat(stockDao).isInstanceOf(StockJpaDao.class);
    }
}
