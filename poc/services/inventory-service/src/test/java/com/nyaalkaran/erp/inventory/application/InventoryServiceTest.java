package com.nyaalkaran.erp.inventory.application;

import com.nyaalkaran.erp.inventory.application.InventoryService.ReceiveUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit;
import com.nyaalkaran.erp.inventory.domain.StockUnit.StockStatus;
import com.nyaalkaran.erp.inventory.domain.StockUnitRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    /** In-memory StockUnitRepository with the same semantics as the SQL implementation. */
    static final class Units implements StockUnitRepository {
        final List<StockUnit> rows = new ArrayList<>();

        @Override
        public StockUnit insert(StockUnit u) {
            StockUnit saved = with(u, (long) rows.size() + 1, u.status(), u.reservedFor());
            rows.add(saved);
            return saved;
        }

        @Override
        public Optional<StockUnit> findByVin(String vin) {
            return rows.stream().filter(u -> u.vin().equals(vin)).findFirst();
        }

        @Override
        public List<StockUnit> find(Filter f) {
            return rows.stream()
                    .filter(u -> f.modelCode() == null || u.modelCode().equals(f.modelCode()))
                    .filter(u -> f.color() == null || u.color().equals(f.color()))
                    .filter(u -> f.status() == null || u.status() == f.status())
                    .toList();
        }

        @Override
        public Optional<StockUnit> findByReservation(String reference) {
            return rows.stream().filter(u -> reference.equals(u.reservedFor())).findFirst();
        }

        @Override
        public boolean reserveFirstAvailable(String model, String color, String reference, LocalDateTime now) {
            for (int i = 0; i < rows.size(); i++) {
                StockUnit u = rows.get(i);
                if (u.modelCode().equals(model) && u.color().equals(color) && u.status() == StockStatus.AVAILABLE) {
                    rows.set(i, with(u, u.id(), StockStatus.RESERVED, reference));
                    return true;
                }
            }
            return false;
        }

        @Override
        public int transition(String reference, StockStatus from, StockStatus to, boolean keep, LocalDateTime now) {
            for (int i = 0; i < rows.size(); i++) {
                StockUnit u = rows.get(i);
                if (Objects.equals(reference, u.reservedFor()) && u.status() == from) {
                    rows.set(i, with(u, u.id(), to, keep ? reference : null));
                    return 1;
                }
            }
            return 0;
        }

        private static StockUnit with(StockUnit u, Long id, StockStatus status, String reservedFor) {
            return new StockUnit(id, u.vin(), u.modelCode(), u.color(), status, reservedFor, u.createdAt(), u.createdBy());
        }
    }

    private final Units units = new Units();
    private final InventoryService service = new InventoryService(units, "VX-SUV"::equals, Clock.systemUTC());

    @Test
    void onlyCatalogModelsCanBeStocked() {
        assertThatThrownBy(() -> service.receive(new ReceiveUnit("MA1VIN00000000009", "OTHER", "RED"), "u"))
                .hasFieldOrPropertyWithValue("code", "UNKNOWN_MODEL");
        StockUnit u = service.receive(new ReceiveUnit("ma1vin00000000001", "vx-suv", "red"), "u");
        assertThat(u.vin()).isEqualTo("MA1VIN00000000001");
        assertThat(u.status()).isEqualTo(StockStatus.AVAILABLE);
        assertThatThrownBy(() -> service.receive(new ReceiveUnit("MA1VIN00000000001", "VX-SUV", "RED"), "u"))
                .hasFieldOrPropertyWithValue("code", "VIN_EXISTS");
    }

    @Test
    void reservationIsIdempotentPerReferenceAndRespectsColor() {
        service.receive(new ReceiveUnit("MA1VIN00000000001", "VX-SUV", "RED"), "u");
        service.receive(new ReceiveUnit("MA1VIN00000000002", "VX-SUV", "BLUE"), "u");
        StockUnit first = service.reserve("BK-1", "VX-SUV", "red");
        assertThat(service.reserve("BK-1", "VX-SUV", "red").vin()).isEqualTo(first.vin());
        assertThatThrownBy(() -> service.reserve("BK-2", "VX-SUV", "RED"))
                .hasFieldOrPropertyWithValue("code", "OUT_OF_STOCK");
        assertThat(service.reserve("BK-3", "VX-SUV", "BLUE").vin()).isEqualTo("MA1VIN00000000002");
    }

    @Test
    void releaseMakesTheCarAvailableAgainAndSellIsFinal() {
        service.receive(new ReceiveUnit("MA1VIN00000000001", "VX-SUV", "RED"), "u");
        service.reserve("BK-1", "VX-SUV", "RED");
        service.release("BK-1");
        assertThat(service.list(null, null, StockStatus.AVAILABLE)).hasSize(1);
        service.reserve("BK-2", "VX-SUV", "RED");
        assertThat(service.sell("BK-2").status()).isEqualTo(StockStatus.SOLD);
        assertThatThrownBy(() -> service.release("BK-2")).hasFieldOrPropertyWithValue("code", "RESERVATION_NOT_FOUND");
    }
}
