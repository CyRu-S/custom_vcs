package com.vcs.customvcs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Packaged releases serve React from the same origin as the API, including bookmarked routes.
@Controller
public class SpaController {
    @GetMapping({"/", "/login", "/register", "/repositories", "/activity", "/settings", "/guide", "/r/{id}/{section}"})
    public String app() { return "forward:/index.html"; }
}
