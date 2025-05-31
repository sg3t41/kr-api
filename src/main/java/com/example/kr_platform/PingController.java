package com.example.kr_platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // このクラスがRESTful APIを扱うコントローラであることを示す
public class PingController {

    // HTTP GET リクエストが '/ping' パスに送られたときにこのメソッドが実行される
    @GetMapping("/ping")
    public String ping() {
        return "Pong!"; // "Pong!" という文字列をクライアントに返す
    }
}
