package com.n11.auth.api;

import com.n11.auth.api.dto.AddressDto;
import com.n11.auth.api.dto.AddressRequest;
import com.n11.auth.service.AddressService;
import com.n11.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "User shipping address book")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddressService service;

    @Operation(summary = "List the authenticated user's addresses (default first)")
    @GetMapping
    public List<AddressDto> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.userId());
    }

    @Operation(summary = "Get one address by id (only if owned by caller)")
    @GetMapping("/{id}")
    public AddressDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.get(user.userId(), id);
    }

    @Operation(summary = "Create a new address; first one is implicitly default")
    @PostMapping
    public ResponseEntity<AddressDto> create(@AuthenticationPrincipal AuthenticatedUser user,
                                             @RequestBody @Valid AddressRequest req) {
        AddressDto dto = service.create(user.userId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Update an existing address")
    @PutMapping("/{id}")
    public AddressDto update(@AuthenticationPrincipal AuthenticatedUser user,
                             @PathVariable Long id,
                             @RequestBody @Valid AddressRequest req) {
        return service.update(user.userId(), id, req);
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        service.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
