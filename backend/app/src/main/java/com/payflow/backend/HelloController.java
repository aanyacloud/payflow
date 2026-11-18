
package com.payflow.backend;

import com.payflow.backend.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HelloController {

    @GetMapping("/hello")
    public ApiResponse sayHello() {
        return new ApiResponse(
                "Welcome to PayFlow API!",
                "PayFlow",
                "running"
        );
    }
}
