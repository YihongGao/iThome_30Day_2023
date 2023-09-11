package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    // 從 application.yml 取此變數值
    @Value("${app.welcome.message}")
    private String welcomeMessage;

    // 從 application.yml 取此變數值
    @Value("${app.env}")
    private String env;

    @RequestMapping("/")
    public String welcome() {
        return welcomeMessage;
    }

    @RequestMapping("/env")
    public String env() {
        return env;
    }
}
