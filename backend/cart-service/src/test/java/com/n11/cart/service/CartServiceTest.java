package com.n11.cart.service;

import com.n11.cart.api.dto.AddItemRequest;
import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.mapper.CartMapper;
import com.n11.cart.client.ProductClient;
import com.n11.cart.client.ProductSnapshot;
import com.n11.cart.domain.Cart;
import com.n11.cart.exception.InsufficientStockException;
import com.n11.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository repository;
    @Mock ProductClient productClient;

    private final CartMapper mapper = Mappers.getMapper(CartMapper.class);
    private CartService service;

    @BeforeEach
    void wire() {
        service = new CartService(repository, productClient, mapper);
    }

    @Test
    void addsNewItemAndComputesTotals() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productClient.fetch(99L)).thenReturn(new ProductSnapshot(
                99L, "iPhone", "img.png", new BigDecimal("64999.00"), "TRY", 5));

        CartDto cart = service.addItem(1L, new AddItemRequest(99L, 2));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
        assertThat(cart.totalAmount()).isEqualByComparingTo("129998.00");
        assertThat(cart.totalQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsAdditionExceedingStock() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productClient.fetch(99L)).thenReturn(new ProductSnapshot(
                99L, "Item", null, new BigDecimal("10.00"), "TRY", 1));

        assertThatThrownBy(() -> service.addItem(1L, new AddItemRequest(99L, 5)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void clearEmptiesItems() {
        Cart cart = Cart.builder().id(7L).userId(1L).build();
        cart.addItem(com.n11.cart.domain.CartItem.builder()
                .productId(1L).productName("X").quantity(1)
                .unitPrice(new BigDecimal("1.0")).currency("TRY").build());

        when(repository.findByUserId(1L)).thenReturn(Optional.of(cart));

        service.clear(1L);

        assertThat(cart.getItems()).isEmpty();
    }
}
