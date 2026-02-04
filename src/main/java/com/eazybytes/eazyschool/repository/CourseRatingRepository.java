package com.eazybytes.eazyschool.repository;

import com.eazybytes.eazyschool.model.CourseRating;
import com.eazybytes.eazyschool.model.Courses;
import com.eazybytes.eazyschool.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRatingRepository extends JpaRepository<CourseRating, Integer> {

    Optional<CourseRating> findByCourseAndStudent(Courses course, Person student);

    List<CourseRating> findByCourse(Courses course);

    @Query("SELECT AVG(r.rating) FROM CourseRating r WHERE r.course = :course")
    Double getAverageRatingByCourse(@Param("course") Courses course);

    @Query("SELECT COUNT(r) FROM CourseRating r WHERE r.course = :course")
    int countByCourse(@Param("course") Courses course);
}
