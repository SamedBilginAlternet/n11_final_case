package com.n11.payment;

import com.n11.payment.config.PaymentProperties;
import com.n11.payment.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableConfigurationProperties({PaymentProperties.class, SecurityProperties.class})
@ComponentScan(basePackages = {"com.n11.payment", "com.n11.common"})
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
