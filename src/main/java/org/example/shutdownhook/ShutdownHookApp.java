package org.example.shutdownhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author chengaofeng
 * @date 2020年10月20日 2:26 下午
 */
@SpringBootApplication
@Slf4j
public class ShutdownHookApp {

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(()->{

           log.info("app shutdown hook executed");
        }));

        SpringApplication.run(ShutdownHookApp.class, args);
    }
}
