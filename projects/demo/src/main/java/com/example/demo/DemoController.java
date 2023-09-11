package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    // 從 application.yml / app.welcome.message 取此變數值
    @Value("${app.welcome.message}")
    private String welcomeMessage;

    // 從 application.yml / app.env 取此變數值
    @Value("${app.env}")
    private String env;

    // 從 application.yml / app.token 取此變數值
    @Value("${app.token}")
    private String token;

    @RequestMapping("/")
    public String welcome() {
        return welcomeMessage;
    }

    @RequestMapping("/env")
    public String env() {
        return env;
    }

    @RequestMapping("/token")
    public String token() {
        return token;
    }
}
