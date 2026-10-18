package com.postiva.admin.config;

import com.postiva.admin.service.AdminProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "postiva.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final AdminProvisioningService provisioning;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrapRunner(AdminProvisioningService provisioning,
            @Value("${postiva.admin.bootstrap.email:}") String email,
            @Value("${postiva.admin.bootstrap.password:}") String password,
            @Value("${postiva.admin.bootstrap.full-name:Postiva Administrator}") String fullName) {
        this.provisioning = provisioning;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisioning.provision(email, password, fullName);
        log.info("Admin bootstrap completed. Disable POSTIVA_ADMIN_BOOTSTRAP_ENABLED after provisioning.");
    }
}
