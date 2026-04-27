package com.n11.auth.service;

import com.n11.auth.api.dto.AddressDto;
import com.n11.auth.api.dto.AddressRequest;
import com.n11.auth.domain.Address;
import com.n11.auth.repository.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock AddressRepository repository;
    @InjectMocks AddressService service;

    private AddressRequest req(boolean def) {
        return new AddressRequest("Ev", "Ada Lovelace", "+905551234567",
                "Bağdat Cd 100", "Istanbul", "Kadıköy", "34710", def);
    }

    @Test
    void firstAddressIsImplicitlyDefault() {
        when(repository.findFirstByUserIdAndDefaultAddressTrue(7L)).thenReturn(Optional.empty());
        when(repository.findByUserIdOrderByDefaultAddressDescIdAsc(7L)).thenReturn(List.of());
        when(repository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        AddressDto dto = service.create(7L, req(false));

        assertThat(dto.defaultAddress()).isTrue();
        verify(repository, times(1)).clearDefaultsFor(7L);
    }

    @Test
    void promotingNewDefaultClearsOldOne() {
        // Existing default present → service short-circuits the second
        // findBy* check; only stub the first one.
        when(repository.findFirstByUserIdAndDefaultAddressTrue(7L))
                .thenReturn(Optional.of(Address.builder().id(99L).userId(7L).defaultAddress(true).build()));
        when(repository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        ArgumentCaptor<Address> saved = ArgumentCaptor.forClass(Address.class);
        AddressDto dto = service.create(7L, req(true));

        verify(repository).clearDefaultsFor(7L);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isDefaultAddress()).isTrue();
        assertThat(dto.defaultAddress()).isTrue();
    }

    @Test
    void nonDefaultRequestStaysNonDefaultWhenOthersExist() {
        when(repository.findFirstByUserIdAndDefaultAddressTrue(7L))
                .thenReturn(Optional.of(Address.builder().id(99L).userId(7L).defaultAddress(true).build()));
        when(repository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(3L);
            return a;
        });

        AddressDto dto = service.create(7L, req(false));

        assertThat(dto.defaultAddress()).isFalse();
        verify(repository, never()).clearDefaultsFor(any());
    }

    @Test
    void rejectsForeignAddressWith404() {
        when(repository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L, 99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
