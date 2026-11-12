package com.sulaksono.egineeringdataservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class EngineeringDataServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
