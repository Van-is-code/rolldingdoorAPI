package api.rollingdoor.service;

import api.rollingdoor.repository.InvitePinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class PinGeneratorService {

    private final InvitePinRepository invitePinRepository;
    private final SecureRandom random = new SecureRandom();

    public String generateUniquePin() {
        String pin;
        do {
            int pinInt = 100000 + random.nextInt(900000);
            pin = String.format("%06d", pinInt);
        } while (invitePinRepository.existsByPin(pin));
        return pin;
    }
}