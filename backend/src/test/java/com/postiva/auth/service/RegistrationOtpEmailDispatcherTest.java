package com.postiva.auth.service;

import com.postiva.auth.repository.PendingRegistrationRepository;
import com.postiva.mail.EmailService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RegistrationOtpEmailDispatcherTest {
    private final EmailService email = mock(EmailService.class);
    private final PendingRegistrationRepository registrations = mock(PendingRegistrationRepository.class);
    private final RegistrationOtpEmailDispatcher dispatcher = new RegistrationOtpEmailDispatcher(email, registrations);

    @Test void successfulDeliveryKeepsPendingOtp() {
        dispatcher.send("person@example.test", "Person", "123456", "bcrypt-hash", 10);

        verify(email).sendRegistrationOtp("person@example.test", "Person", "123456", 10);
        verifyNoInteractions(registrations);
    }

    @Test void failedDeliveryInvalidatesOnlyTheOtpThatWasSent() {
        doThrow(new IllegalStateException("provider details"))
                .when(email).sendRegistrationOtp("person@example.test", "Person", "123456", 10);

        dispatcher.send("person@example.test", "Person", "123456", "bcrypt-hash", 10);

        verify(registrations).deleteByEmailIgnoreCaseAndOtpHash("person@example.test", "bcrypt-hash");
    }
}
