package de.gaalop.rest.api;

import de.gaalop.CompilationException;
import de.gaalop.rest.dto.CompileRequest;
import de.gaalop.rest.dto.CompileResponse;
import de.gaalop.rest.service.GaalopCompileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CompileController {

    private final GaalopCompileService compileService;

    public CompileController(GaalopCompileService compileService) {
        this.compileService = compileService;
    }

    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) throws CompilationException {
        return compileService.compile(request);
    }
}
