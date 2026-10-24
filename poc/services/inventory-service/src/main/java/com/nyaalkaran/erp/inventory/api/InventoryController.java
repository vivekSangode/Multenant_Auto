package com.nyaalkaran.erp.inventory.api;

import com.nyaalkaran.erp.inventory.application.InventoryService;
import com.nyaalkaran.erp.inventory.application.InventoryService.ReceiveUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit.StockStatus;
import com.nyaalkaran.erp.tenant.context.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    public record ReceiveUnitRequest(@NotBlank @Pattern(regexp = "[A-HJ-NPR-Za-hj-npr-z0-9]{17}",
                                             message = "must be a 17-character VIN") String vin,
                                     @NotBlank @Size(max = 40) String modelCode,
                                     @NotBlank @Size(max = 40) String color) {
    }

    public record ReserveRequest(@NotBlank @Size(max = 40) String reference,
                                 @NotBlank @Size(max = 40) String modelCode,
                                 @NotBlank @Size(max = 40) String color) {
    }

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public StockUnit receive(@Valid @RequestBody ReceiveUnitRequest req) {
        return inventory.receive(new ReceiveUnit(req.vin(), req.modelCode(), req.color()),
                TenantContext.require().username());
    }

    @GetMapping("/units")
    public List<StockUnit> list(@RequestParam(required = false) String modelCode,
                                @RequestParam(required = false) String color,
                                @RequestParam(required = false) StockStatus status) {
        return inventory.list(modelCode, color, status);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public StockUnit reserve(@Valid @RequestBody ReserveRequest req) {
        return inventory.reserve(req.reference(), req.modelCode(), req.color());
    }

    @DeleteMapping("/reservations/{reference}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable String reference) {
        inventory.release(reference);
    }

    @PostMapping("/reservations/{reference}/sell")
    public StockUnit sell(@PathVariable String reference) {
        return inventory.sell(reference);
    }
}
