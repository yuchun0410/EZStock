package com.ezstock.dao;

import com.ezstock.dao.mybatis.StockMyBatisDao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"ezstock.dao.type=mybatis", "ezstock.import.run-on-startup=false"})
class MyBatisDaoTest extends AbstractDaoTest {

    @Test
    void usesMyBatisImplementation() {
        assertThat(stockDao).isInstanceOf(StockMyBatisDao.class);
    }
}
