package com.xksgroup.m3u8encoderv2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping
    private String redirectToSwagger(){

        return "redirect:/swagger-ui.html";

    }
}
