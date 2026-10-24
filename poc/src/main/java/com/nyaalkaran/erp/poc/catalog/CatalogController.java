package com.nyaalkaran.erp.poc.catalog;

import com.nyaalkaran.erp.poc.catalog.CatalogEnums.BillingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    /** Modules, bundles, dependencies and the current price list for one billing period. */
    @GetMapping
    public CatalogSnapshot catalog(@RequestParam(defaultValue = "MONTHLY") BillingPeriod period,
                                   @RequestParam(defaultValue = "INR") String currency) {
        return catalogService.snapshot(period, currency);
    }
}
