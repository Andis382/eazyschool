package com.eazybytes.eazyschool.controller;

import com.eazybytes.eazyschool.model.*;
import com.eazybytes.eazyschool.repository.*;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("student")
public class StudentController {

    private static final String UPLOAD_DIR = "uploads/course-materials/";

    @Autowired
    private CoursesRepository coursesRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CourseRatingRepository courseRatingRepository;

    @Autowired
    private CourseEnrollmentRequestRepository enrollmentRequestRepository;

    @Autowired
    private CourseMaterialRepository courseMaterialRepository;

    @GetMapping("/displayCourses")
    public ModelAndView displayCourses(Model model, HttpSession session) {
        Person person = (Person) session.getAttribute("loggedInPerson");
        ModelAndView modelAndView = new ModelAndView("courses_enrolled.html");
        modelAndView.addObject("person", person);
        return modelAndView;
    }

    @GetMapping("/viewCourse")
    public ModelAndView viewCourse(@RequestParam int id, HttpSession session,
                                   @RequestParam(required = false) String success,
                                   @RequestParam(required = false) String error) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        Optional<Courses> courseOpt = coursesRepository.findById(id);

        if (courseOpt.isEmpty()) {
            return new ModelAndView("redirect:/courses");
        }

        Courses course = courseOpt.get();
        ModelAndView modelAndView;

        // Calculate average rating
        Double avgRating = courseRatingRepository.getAverageRatingByCourse(course);
        double rating = avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0;
        int ratingCount = courseRatingRepository.countByCourse(course);

        // Check if user is logged in and is a student
        boolean isLoggedIn = loggedInPerson != null;
        boolean isEnrolled = false;
        CourseRating existingRating = null;
        boolean hasPendingEnrollRequest = false;
        boolean hasPendingUnenrollRequest = false;

        if (isLoggedIn) {
            // Refresh person from DB to get updated courses
            Person refreshedPerson = personRepository.findById(loggedInPerson.getPersonId()).orElse(loggedInPerson);
            isEnrolled = refreshedPerson.getCourses().stream()
                    .anyMatch(c -> c.getCourseId() == id);

            // Check for existing rating
            existingRating = courseRatingRepository.findByCourseAndStudent(course, refreshedPerson).orElse(null);

            // Check for pending requests
            hasPendingEnrollRequest = enrollmentRequestRepository
                    .existsByCourseAndStudentAndRequestTypeAndStatus(course, refreshedPerson, "ENROLL", "PENDING");
            hasPendingUnenrollRequest = enrollmentRequestRepository
                    .existsByCourseAndStudentAndRequestTypeAndStatus(course, refreshedPerson, "UNENROLL", "PENDING");

            session.setAttribute("loggedInPerson", refreshedPerson);
        }

        if (isEnrolled) {
            modelAndView = new ModelAndView("student_course_enrolled.html");
            // Get course materials
            List<CourseMaterial> materials = courseMaterialRepository.findByCourseOrderByCreatedAtDesc(course);
            modelAndView.addObject("materials", materials);
            modelAndView.addObject("existingRating", existingRating);
            modelAndView.addObject("hasPendingUnenrollRequest", hasPendingUnenrollRequest);
        } else {
            modelAndView = new ModelAndView("student_course_not_enrolled.html");
            modelAndView.addObject("hasPendingEnrollRequest", hasPendingEnrollRequest);
        }

        modelAndView.addObject("course", course);
        modelAndView.addObject("avgRating", rating);
        modelAndView.addObject("ratingCount", ratingCount);
        modelAndView.addObject("isLoggedIn", isLoggedIn);

        if (success != null) {
            modelAndView.addObject("successMessage", success);
        }
        if (error != null) {
            modelAndView.addObject("errorMessage", error);
        }

        return modelAndView;
    }

    @PostMapping("/requestEnrollment")
    public String requestEnrollment(@RequestParam int courseId, HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        if (loggedInPerson == null) {
            return "redirect:/login";
        }

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kursi nuk u gjet!");
            return "redirect:/courses";
        }

        Courses course = courseOpt.get();

        // Check if already has pending request
        if (enrollmentRequestRepository.existsByCourseAndStudentAndRequestTypeAndStatus(
                course, loggedInPerson, "ENROLL", "PENDING")) {
            redirectAttributes.addAttribute("error", "Keni tashme nje kerkese ne pritje per kete kurs!");
            return "redirect:/student/viewCourse?id=" + courseId;
        }

        // Create enrollment request
        CourseEnrollmentRequest request = new CourseEnrollmentRequest();
        request.setCourse(course);
        request.setStudent(loggedInPerson);
        request.setRequestType("ENROLL");
        request.setStatus("PENDING");
        enrollmentRequestRepository.save(request);

        redirectAttributes.addAttribute("success", "Kerkesa per regjistrim u dergua me sukses!");
        return "redirect:/student/viewCourse?id=" + courseId;
    }

    @PostMapping("/requestUnenrollment")
    public String requestUnenrollment(@RequestParam int courseId, HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        if (loggedInPerson == null) {
            return "redirect:/login";
        }

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kursi nuk u gjet!");
            return "redirect:/courses";
        }

        Courses course = courseOpt.get();

        // Check if already has pending request
        if (enrollmentRequestRepository.existsByCourseAndStudentAndRequestTypeAndStatus(
                course, loggedInPerson, "UNENROLL", "PENDING")) {
            redirectAttributes.addAttribute("error", "Keni tashme nje kerkese ne pritje per te hequr regjistrimin!");
            return "redirect:/student/viewCourse?id=" + courseId;
        }

        // Create unenrollment request
        CourseEnrollmentRequest request = new CourseEnrollmentRequest();
        request.setCourse(course);
        request.setStudent(loggedInPerson);
        request.setRequestType("UNENROLL");
        request.setStatus("PENDING");
        enrollmentRequestRepository.save(request);

        redirectAttributes.addAttribute("success", "Kerkesa per heqje regjistrimi u dergua me sukses!");
        return "redirect:/student/viewCourse?id=" + courseId;
    }

    @PostMapping("/rateCourse")
    public String rateCourse(@RequestParam int courseId, @RequestParam int rating,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        if (loggedInPerson == null) {
            return "redirect:/login";
        }

        if (rating < 1 || rating > 5) {
            redirectAttributes.addAttribute("error", "Vleresimi duhet te jete mes 1 dhe 5!");
            return "redirect:/student/viewCourse?id=" + courseId;
        }

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kursi nuk u gjet!");
            return "redirect:/courses";
        }

        Courses course = courseOpt.get();

        // Check if already rated
        Optional<CourseRating> existingRating = courseRatingRepository.findByCourseAndStudent(course, loggedInPerson);
        if (existingRating.isPresent()) {
            redirectAttributes.addAttribute("error", "Keni bere tashme nje vleresim per kete kurs!");
            return "redirect:/student/viewCourse?id=" + courseId;
        }

        // Create rating
        CourseRating courseRating = new CourseRating();
        courseRating.setCourse(course);
        courseRating.setStudent(loggedInPerson);
        courseRating.setRating(rating);
        courseRatingRepository.save(courseRating);

        redirectAttributes.addAttribute("success", "Vleresimi u ruajt me sukses!");
        return "redirect:/student/viewCourse?id=" + courseId;
    }

    @GetMapping("/downloadMaterial/{courseId}/{materialId}")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable int courseId,
                                                     @PathVariable int materialId,
                                                     HttpSession session) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        if (loggedInPerson == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<CourseMaterial> materialOpt = courseMaterialRepository.findById(materialId);
        if (materialOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CourseMaterial material = materialOpt.get();
        Courses course = material.getCourse();

        // Verify student is enrolled in this course
        Person refreshedPerson = personRepository.findById(loggedInPerson.getPersonId()).orElse(loggedInPerson);
        boolean isEnrolled = refreshedPerson.getCourses().stream()
                .anyMatch(c -> c.getCourseId() == courseId);

        if (!isEnrolled) {
            return ResponseEntity.status(403).build();
        }

        try {
            Path filePath = Paths.get(UPLOAD_DIR + courseId + "/" + material.getFileName());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + material.getOriginalFileName() + "\"")
                        .body(resource);
            }
        } catch (MalformedURLException e) {
            log.error("Error downloading material", e);
        }

        return ResponseEntity.notFound().build();
    }
}
