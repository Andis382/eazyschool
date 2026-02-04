package com.eazybytes.eazyschool.repository;

import com.eazybytes.eazyschool.model.CourseMaterial;
import com.eazybytes.eazyschool.model.Courses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Integer> {

    List<CourseMaterial> findByCourse(Courses course);

    int countByCourse(Courses course);

    List<CourseMaterial> findByCourseOrderByCreatedAtDesc(Courses course);
}
