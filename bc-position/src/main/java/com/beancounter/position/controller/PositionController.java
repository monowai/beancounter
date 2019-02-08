package com.beancounter.position.controller;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.position.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping("/")
public class PositionController {

    private PositionService positionService;

    @Autowired
    PositionController(PositionService positionService){
        this.positionService = positionService;
    }

    @GetMapping(value = "/{assetId}", produces = "application/json")
    MarketData getPrice(@PathVariable("assetId") String assetId) {
        try {
            return positionService.getPrice(assetId);
        } catch ( BusinessException be ){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
        }
    }
}