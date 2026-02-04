package com.eazybytes.eazyschool.config;

import com.eazybytes.eazyschool.constants.EazySchoolConstants;
import com.eazybytes.eazyschool.model.Roles;
import com.eazybytes.eazyschool.repository.RolesRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RolesRepository rolesRepository;

    @Override
    public void run(String... args) {
        initializeRoles();
    }

    private void initializeRoles() {
        // Check and create LECTURER role if it doesn't exist
        Roles lecturerRole = rolesRepository.getByRoleName(EazySchoolConstants.LECTURER_ROLE);
        if (lecturerRole == null) {
            Roles newLecturerRole = new Roles();
            newLecturerRole.setRoleName(EazySchoolConstants.LECTURER_ROLE);
            rolesRepository.save(newLecturerRole);
            log.info("LECTURER role created successfully");
        }

        // Ensure STUDENT role exists
        Roles studentRole = rolesRepository.getByRoleName(EazySchoolConstants.STUDENT_ROLE);
        if (studentRole == null) {
            Roles newStudentRole = new Roles();
            newStudentRole.setRoleName(EazySchoolConstants.STUDENT_ROLE);
            rolesRepository.save(newStudentRole);
            log.info("STUDENT role created successfully");
        }

        // Ensure ADMIN role exists
        Roles adminRole = rolesRepository.getByRoleName(EazySchoolConstants.ADMIN_ROLE);
        if (adminRole == null) {
            Roles newAdminRole = new Roles();
            newAdminRole.setRoleName(EazySchoolConstants.ADMIN_ROLE);
            rolesRepository.save(newAdminRole);
            log.info("ADMIN role created successfully");
        }
    }
}
