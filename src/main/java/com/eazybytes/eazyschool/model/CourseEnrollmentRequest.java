package com.eazybytes.eazyschool.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "course_enrollment_requests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"course_id", "student_id", "request_type"})
})
public class CourseEnrollmentRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private int requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", referencedColumnName = "courseId", nullable = false)
    private Courses course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "personId", nullable = false)
    private Person student;

    @Column(name = "request_type", nullable = false)
    private String requestType; // "ENROLL" or "UNENROLL"

    @Column(nullable = false)
    private String status; // "PENDING", "APPROVED", "REJECTED"
}
