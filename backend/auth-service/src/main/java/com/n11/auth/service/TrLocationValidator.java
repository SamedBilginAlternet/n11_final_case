package com.n11.auth.service;

import com.n11.auth.api.dto.AddressRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class TrLocationValidator implements ConstraintValidator<ValidTrLocation, AddressRequest> {

    private final TrLocationCatalog catalog;

    public TrLocationValidator(TrLocationCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean isValid(AddressRequest req, ConstraintValidatorContext ctx) {
        if (req == null) return true;   // @NotNull elsewhere

        boolean cityOk = catalog.isValidCity(req.city());
        boolean districtOk = catalog.isValidDistrict(req.city(), req.district());

        if (cityOk && districtOk) return true;

        // Replace the default message with field-targeted ones so the client
        // can highlight the right input.  Disabling the default keeps the
        // class-level message from leaking when only one field is wrong.
        ctx.disableDefaultConstraintViolation();
        if (!cityOk) {
            ctx.buildConstraintViolationWithTemplate("Geçersiz il")
                    .addPropertyNode("city").addConstraintViolation();
        } else {
            ctx.buildConstraintViolationWithTemplate("Bu ile ait geçersiz ilçe")
                    .addPropertyNode("district").addConstraintViolation();
        }
        return false;
    }
}
