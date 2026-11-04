package com.portafolio.banca.riesgo.api;

import com.portafolio.banca.riesgo.application.RiskService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/evaluaciones")
public class InternalRiskController {
    private final RiskService service;

    public InternalRiskController(RiskService service) {
        this.service = service;
    }

    @PostMapping
    public EvaluationResponse evaluate(@RequestBody EvaluationRequest request) {
        return service.evaluate(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void invalid() {}

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void conflict() {}
}
