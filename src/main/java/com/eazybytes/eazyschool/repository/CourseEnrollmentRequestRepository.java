package com.eazybytes.eazyschool.repository;

import com.eazybytes.eazyschool.model.CourseEnrollmentRequest;
import com.eazybytes.eazyschool.model.Courses;
import com.eazybytes.eazyschool.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseEnrollmentRequestRepository extends JpaRepository<CourseEnrollmentRequest, Integer> {

    Optional<CourseEnrollmentRequest> findByCourseAndStudentAndRequestType(Courses course, Person student, String requestType);

    List<CourseEnrollmentRequest> findByStatus(String status);

    List<CourseEnrollmentRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<CourseEnrollmentRequest> findByStudentAndCourse(Person student, Courses course);

    boolean existsByCourseAndStudentAndRequestTypeAndStatus(Courses course, Person student, String requestType, String status);
}
