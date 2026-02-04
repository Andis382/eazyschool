package com.eazybytes.eazyschool.repository;

import com.eazybytes.eazyschool.model.Person;
import com.eazybytes.eazyschool.model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Integer> {

    Person readByEmail(String email);

    List<Person> findByRoles(Roles roles);

    List<Person> findByRolesOrderByName(Roles roles);

}
