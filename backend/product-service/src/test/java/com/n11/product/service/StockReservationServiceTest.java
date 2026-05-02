package com.n11.product.service;

import com.n11.product.repository.ProductRepository;
import com.n11.product.service.StockReservationService.InsufficientStockException;
import com.n11.product.service.StockReservationService.StockItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock ProductRepository repository;
    @InjectMocks StockReservationService service;

    @Test
    void reservesAllItemsWhenStockSufficient() {
        when(repository.decrementStockIfAvailable(1L, 2)).thenReturn(1);
        when(repository.decrementStockIfAvailable(2L, 1)).thenReturn(1);

        var result = service.reserve(List.of(new StockItem(1L, 2), new StockItem(2L, 1)));

        assertThat(result.ok()).isTrue();
        assertThat(result.insufficientProductIds()).isEmpty();
    }

    @Test
    void throwsAndRollsBackWhenAnyItemShort() {
        // Item 1 succeeds, item 2 fails.  The exception aborts the
        // transaction so item 1's decrement is undone by Spring; here we
        // just verify the contract — the service throws a sentinel listing
        // the offending ids.
        when(repository.decrementStockIfAvailable(1L, 2)).thenReturn(1);
        when(repository.decrementStockIfAvailable(2L, 5)).thenReturn(0);

        assertThatThrownBy(() ->
                service.reserve(List.of(new StockItem(1L, 2), new StockItem(2L, 5))))
                .isInstanceOf(InsufficientStockException.class)
                .matches(t -> ((InsufficientStockException) t).productIds().equals(List.of(2L)));
    }

    @Test
    void releaseIncrementsEveryItem() {
        service.release(List.of(new StockItem(1L, 3), new StockItem(2L, 4)));

        verify(repository).incrementStock(eq(1L), eq(3));
        verify(repository).incrementStock(eq(2L), eq(4));
    }

    @Test
    void emptyReserveSucceedsTrivially() {
        var result = service.reserve(List.of());
        assertThat(result.ok()).isTrue();
        verify(repository, never()).decrementStockIfAvailable(eq(1L), eq(1));
    }
}
