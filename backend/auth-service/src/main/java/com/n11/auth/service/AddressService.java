package com.n11.auth.service;

import com.n11.auth.api.dto.AddressDto;
import com.n11.auth.api.dto.AddressRequest;
import com.n11.auth.domain.Address;
import com.n11.auth.repository.AddressRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository repository;

    @Transactional(readOnly = true)
    public List<AddressDto> list(Long userId) {
        return repository.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream()
                .map(AddressService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressDto get(Long userId, Long id) {
        return toDto(load(userId, id));
    }

    @Transactional
    public AddressDto create(Long userId, AddressRequest req) {
        boolean firstAddress = repository.findFirstByUserIdAndDefaultAddressTrue(userId).isEmpty()
                && repository.findByUserIdOrderByDefaultAddressDescIdAsc(userId).isEmpty();

        // First address is implicitly default; otherwise honor request.
        boolean shouldBeDefault = firstAddress || req.defaultAddress();
        if (shouldBeDefault) {
            repository.clearDefaultsFor(userId);
        }

        Address entity = Address.builder()
                .userId(userId)
                .addressType(req.addressType())
                .title(req.title())
                .recipientName(req.recipientName())
                .phone(req.phone())
                .line1(req.line1())
                .city(req.city())
                .district(req.district())
                .postalCode(req.postalCode())
                .defaultAddress(shouldBeDefault)
                .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public AddressDto update(Long userId, Long id, AddressRequest req) {
        Address entity = load(userId, id);

        if (req.defaultAddress() && !entity.isDefaultAddress()) {
            repository.clearDefaultsFor(userId);
            entity.setDefaultAddress(true);
        } else if (!req.defaultAddress() && entity.isDefaultAddress()) {
            // Allow turning OFF the default explicitly. We'd rather have zero
            // defaults than fight the user — checkout will just make them pick.
            entity.setDefaultAddress(false);
        }

        entity.setAddressType(req.addressType());
        entity.setTitle(req.title());
        entity.setRecipientName(req.recipientName());
        entity.setPhone(req.phone());
        entity.setLine1(req.line1());
        entity.setCity(req.city());
        entity.setDistrict(req.district());
        entity.setPostalCode(req.postalCode());
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Address entity = load(userId, id);
        repository.delete(entity);
    }

    private Address load(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("address.not_found"));
    }

    public static AddressDto toDto(Address a) {
        return new AddressDto(a.getId(), a.getAddressType(), a.getTitle(), a.getRecipientName(),
                a.getPhone(), a.getLine1(), a.getCity(), a.getDistrict(),
                a.getPostalCode(), a.isDefaultAddress());
    }
}
