package com.eazybytes.eazyschool.controller;

import com.eazybytes.eazyschool.constants.EazySchoolConstants;
import com.eazybytes.eazyschool.model.CourseMaterial;
import com.eazybytes.eazyschool.model.Courses;
import com.eazybytes.eazyschool.model.Person;
import com.eazybytes.eazyschool.repository.CourseMaterialRepository;
import com.eazybytes.eazyschool.repository.CoursesRepository;
import com.eazybytes.eazyschool.repository.PersonRepository;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("lecturer")
public class LecturerController {

    private static final String UPLOAD_DIR = "uploads/course-materials/";

    @Autowired
    private CoursesRepository coursesRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CourseMaterialRepository courseMaterialRepository;

    @GetMapping("/displayCourses")
    public ModelAndView displayLecturerCourses(HttpSession session) {
        ModelAndView modelAndView = new ModelAndView("lecturer_courses.html");
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");
        List<Courses> courses = coursesRepository.findByLecturerOrderByName(loggedInPerson);
        modelAndView.addObject("courses", courses);
        return modelAndView;
    }

    @GetMapping("/viewCourse")
    public ModelAndView viewCourse(@RequestParam int id, HttpSession session,
                                   @RequestParam(required = false) String error,
                                   @RequestParam(required = false) String success) {
        ModelAndView modelAndView = new ModelAndView("lecturer_course_detail.html");
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");

        Optional<Courses> courseOpt = coursesRepository.findById(id);
        if (courseOpt.isEmpty()) {
            modelAndView.setViewName("redirect:/lecturer/displayCourses");
            return modelAndView;
        }

        Courses course = courseOpt.get();

        // Verify this lecturer owns this course
        if (course.getLecturer() == null ||
            course.getLecturer().getPersonId() != loggedInPerson.getPersonId()) {
            modelAndView.setViewName("redirect:/lecturer/displayCourses");
            return modelAndView;
        }

        List<CourseMaterial> materials = courseMaterialRepository.findByCourseOrderByCreatedAtDesc(course);
        int materialCount = materials.size();

        modelAndView.addObject("course", course);
        modelAndView.addObject("materials", materials);
        modelAndView.addObject("materialCount", materialCount);
        modelAndView.addObject("maxMaterials", EazySchoolConstants.MAX_DOCUMENTS_PER_COURSE);
        modelAndView.addObject("canUpload", materialCount < EazySchoolConstants.MAX_DOCUMENTS_PER_COURSE);

        if (error != null) {
            modelAndView.addObject("errorMessage", error);
        }
        if (success != null) {
            modelAndView.addObject("successMessage", success);
        }

        session.setAttribute("currentCourse", course);
        return modelAndView;
    }

    @PostMapping("/uploadMaterial")
    public String uploadMaterial(@RequestParam("file") MultipartFile file,
                                 @RequestParam("courseId") int courseId,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kursi nuk u gjet!");
            return "redirect:/lecturer/displayCourses";
        }

        Courses course = courseOpt.get();

        // Verify ownership
        if (course.getLecturer() == null ||
            course.getLecturer().getPersonId() != loggedInPerson.getPersonId()) {
            redirectAttributes.addAttribute("error", "Nuk keni akses ne kete kurs!");
            return "redirect:/lecturer/displayCourses";
        }

        // Check if file is empty
        if (file.isEmpty()) {
            redirectAttributes.addAttribute("error", "Ju lutem zgjidhni nje dokument per te ngarkuar!");
            return "redirect:/lecturer/viewCourse?id=" + courseId;
        }

        // Check file type - only PDF allowed
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (contentType == null || !contentType.equals("application/pdf") ||
            originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            redirectAttributes.addAttribute("error", "Vetem dokumentet PDF lejohen!");
            return "redirect:/lecturer/viewCourse?id=" + courseId;
        }

        // Check file size - max 10MB
        if (file.getSize() > EazySchoolConstants.MAX_FILE_SIZE) {
            redirectAttributes.addAttribute("error", "Madhesia maksimale e dokumentit eshte 10MB!");
            return "redirect:/lecturer/viewCourse?id=" + courseId;
        }

        // Check document count
        int currentCount = courseMaterialRepository.countByCourse(course);
        if (currentCount >= EazySchoolConstants.MAX_DOCUMENTS_PER_COURSE) {
            redirectAttributes.addAttribute("error", "Numri maksimal i dokumenteve (" +
                EazySchoolConstants.MAX_DOCUMENTS_PER_COURSE + ") eshte arritur per kete kurs!");
            return "redirect:/lecturer/viewCourse?id=" + courseId;
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(UPLOAD_DIR + courseId);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFilename;
            Path filePath = uploadPath.resolve(uniqueFileName);

            // Save file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Save to database
            CourseMaterial material = new CourseMaterial();
            material.setFileName(uniqueFileName);
            material.setOriginalFileName(originalFilename);
            material.setFileSize(file.getSize());
            material.setContentType(contentType);
            material.setCourse(course);

            courseMaterialRepository.save(material);

            log.info("Material uploaded successfully: {} for course: {}", originalFilename, course.getName());
            redirectAttributes.addAttribute("success", "Dokumenti u ngarkua me sukses!");

        } catch (IOException e) {
            log.error("Failed to upload material", e);
            redirectAttributes.addAttribute("error", "Ndodhi nje gabim gjate ngarkimit te dokumentit!");
        }

        return "redirect:/lecturer/viewCourse?id=" + courseId;
    }

    @GetMapping("/deleteMaterial")
    public String deleteMaterial(@RequestParam int materialId,
                                 @RequestParam int courseId,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");

        Optional<CourseMaterial> materialOpt = courseMaterialRepository.findById(materialId);
        if (materialOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Dokumenti nuk u gjet!");
            return "redirect:/lecturer/viewCourse?id=" + courseId;
        }

        CourseMaterial material = materialOpt.get();
        Courses course = material.getCourse();

        // Verify ownership
        if (course.getLecturer() == null ||
            course.getLecturer().getPersonId() != loggedInPerson.getPersonId()) {
            redirectAttributes.addAttribute("error", "Nuk keni akses per te fshire kete dokument!");
            return "redirect:/lecturer/displayCourses";
        }

        try {
            // Delete file from filesystem
            Path filePath = Paths.get(UPLOAD_DIR + courseId + "/" + material.getFileName());
            Files.deleteIfExists(filePath);

            // Delete from database
            courseMaterialRepository.delete(material);

            log.info("Material deleted successfully: {} from course: {}",
                material.getOriginalFileName(), course.getName());
            redirectAttributes.addAttribute("success", "Dokumenti u fshi me sukses!");

        } catch (IOException e) {
            log.error("Failed to delete material file", e);
            redirectAttributes.addAttribute("error", "Ndodhi nje gabim gjate fshirjes se dokumentit!");
        }

        return "redirect:/lecturer/viewCourse?id=" + courseId;
    }

    @GetMapping("/downloadMaterial/{courseId}/{materialId}")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable int courseId,
                                                     @PathVariable int materialId,
                                                     HttpSession session) {
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");

        Optional<CourseMaterial> materialOpt = courseMaterialRepository.findById(materialId);
        if (materialOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CourseMaterial material = materialOpt.get();
        Courses course = material.getCourse();

        // Verify ownership
        if (course.getLecturer() == null ||
            course.getLecturer().getPersonId() != loggedInPerson.getPersonId()) {
            return ResponseEntity.notFound().build();
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

    @GetMapping("/viewStudents")
    public ModelAndView viewStudents(@RequestParam int courseId, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView("lecturer_course_students.html");
        Person loggedInPerson = (Person) session.getAttribute("loggedInPerson");

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            modelAndView.setViewName("redirect:/lecturer/displayCourses");
            return modelAndView;
        }

        Courses course = courseOpt.get();

        // Verify ownership
        if (course.getLecturer() == null ||
            course.getLecturer().getPersonId() != loggedInPerson.getPersonId()) {
            modelAndView.setViewName("redirect:/lecturer/displayCourses");
            return modelAndView;
        }

        modelAndView.addObject("course", course);
        modelAndView.addObject("students", course.getPersons());
        return modelAndView;
    }
}
