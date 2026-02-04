package com.eazybytes.eazyschool.controller;

import com.eazybytes.eazyschool.model.*;
import com.eazybytes.eazyschool.repository.CourseEnrollmentRequestRepository;
import com.eazybytes.eazyschool.repository.CoursesRepository;
import com.eazybytes.eazyschool.repository.EazyClassRepository;
import com.eazybytes.eazyschool.repository.PersonRepository;
import com.eazybytes.eazyschool.service.PersonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("admin")
public class AdminController {

    private static final String COURSE_IMAGE_DIR = "uploads/course-images/";

    @Autowired
    EazyClassRepository eazyClassRepository;

    @Autowired
    PersonRepository personRepository;

    @Autowired
    CoursesRepository coursesRepository;

    @Autowired
    PersonService personService;

    @Autowired
    CourseEnrollmentRequestRepository enrollmentRequestRepository;

    @RequestMapping("/displayClasses")
    public ModelAndView displayClasses(Model model) {
        List<EazyClass> eazyClasses = eazyClassRepository.findAll();
        ModelAndView modelAndView = new ModelAndView("classes.html");
        modelAndView.addObject("eazyClasses",eazyClasses);
        modelAndView.addObject("eazyClass", new EazyClass());
        return modelAndView;
    }

    @PostMapping("/addNewClass")
    public ModelAndView addNewClass(Model model, @ModelAttribute("eazyClass") EazyClass eazyClass) {
        eazyClassRepository.save(eazyClass);
        ModelAndView modelAndView = new ModelAndView("redirect:/admin/displayClasses");
        return modelAndView;
    }

    @RequestMapping("/deleteClass")
    public ModelAndView deleteClass(Model model, @RequestParam int id) {
        Optional<EazyClass> eazyClass = eazyClassRepository.findById(id);
        for(Person person : eazyClass.get().getPersons()){
            person.setEazyClass(null);
            personRepository.save(person);
        }
        eazyClassRepository.deleteById(id);
        ModelAndView modelAndView = new ModelAndView("redirect:/admin/displayClasses");
        return modelAndView;
    }

    @GetMapping("/displayStudents")
    public ModelAndView displayStudents(Model model, @RequestParam int classId, HttpSession session,
                                        @RequestParam(value = "error", required = false) String error) {
        String errorMessage = null;
        ModelAndView modelAndView = new ModelAndView("students.html");
        Optional<EazyClass> eazyClass = eazyClassRepository.findById(classId);
        modelAndView.addObject("eazyClass",eazyClass.get());
        modelAndView.addObject("person",new Person());
        session.setAttribute("eazyClass",eazyClass.get());
        if(error != null) {
            errorMessage = "Invalid Email entered!!";
            modelAndView.addObject("errorMessage", errorMessage);
        }
        return modelAndView;
    }

    @PostMapping("/addStudent")
    public ModelAndView addStudent(Model model, @ModelAttribute("person") Person person, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        EazyClass eazyClass = (EazyClass) session.getAttribute("eazyClass");
        Person personEntity = personRepository.readByEmail(person.getEmail());
        if(personEntity==null || !(personEntity.getPersonId()>0)){
            modelAndView.setViewName("redirect:/admin/displayStudents?classId="+eazyClass.getClassId()
                    +"&error=true");
            return modelAndView;
        }
        personEntity.setEazyClass(eazyClass);
        personRepository.save(personEntity);
        eazyClass.getPersons().add(personEntity);
        eazyClassRepository.save(eazyClass);
        modelAndView.setViewName("redirect:/admin/displayStudents?classId="+eazyClass.getClassId());
        return modelAndView;
    }

    @GetMapping("/deleteStudent")
    public ModelAndView deleteStudent(Model model, @RequestParam int personId, HttpSession session) {
        EazyClass eazyClass = (EazyClass) session.getAttribute("eazyClass");
        Optional<Person> person = personRepository.findById(personId);
        person.get().setEazyClass(null);
        eazyClass.getPersons().remove(person.get());
        EazyClass eazyClassSaved = eazyClassRepository.save(eazyClass);
        session.setAttribute("eazyClass",eazyClassSaved);
        ModelAndView modelAndView = new ModelAndView("redirect:/admin/displayStudents?classId="+eazyClass.getClassId());
        return modelAndView;
    }

    @GetMapping("/displayCourses")
    public ModelAndView displayCourses(Model model) {
        //List<Courses> courses = coursesRepository.findByOrderByNameDesc();
        List<Courses> courses = coursesRepository.findAll(Sort.by("name").descending());
        ModelAndView modelAndView = new ModelAndView("courses_secure.html");
        modelAndView.addObject("courses",courses);
        modelAndView.addObject("course", new Courses());
        return modelAndView;
    }

    @PostMapping("/addNewCourse")
    public String addNewCourse(@ModelAttribute("course") Courses course,
                               @RequestParam(value = "image", required = false) MultipartFile image,
                               RedirectAttributes redirectAttributes) {
        try {
            // Validate description length
            if (course.getDescription() != null && course.getDescription().length() > 600) {
                redirectAttributes.addAttribute("error", "Pershkrimi nuk mund te jete me shume se 600 karaktere!");
                return "redirect:/admin/displayCourses";
            }

            // Handle image upload
            if (image != null && !image.isEmpty()) {
                String contentType = image.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    redirectAttributes.addAttribute("error", "Vetem imazhe lejohen!");
                    return "redirect:/admin/displayCourses";
                }

                // Create upload directory if not exists
                Path uploadPath = Paths.get(COURSE_IMAGE_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                // Generate unique filename
                String originalFilename = image.getOriginalFilename();
                String extension = originalFilename != null ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String uniqueFileName = UUID.randomUUID().toString() + extension;
                Path filePath = uploadPath.resolve(uniqueFileName);

                // Save file
                Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                course.setImagePath(uniqueFileName);
            }

            coursesRepository.save(course);
            redirectAttributes.addAttribute("success", "Kursi u shtua me sukses!");
        } catch (IOException e) {
            log.error("Failed to upload course image", e);
            redirectAttributes.addAttribute("error", "Ndodhi nje gabim gjate ngarkimit te imazhit!");
        }
        return "redirect:/admin/displayCourses";
    }

    @GetMapping("/viewStudents")
    public ModelAndView viewStudents(Model model, @RequestParam int id
                 ,HttpSession session,@RequestParam(required = false) String error) {
        String errorMessage = null;
        ModelAndView modelAndView = new ModelAndView("course_students.html");
        Optional<Courses> courses = coursesRepository.findById(id);
        modelAndView.addObject("courses",courses.get());
        modelAndView.addObject("person",new Person());
        session.setAttribute("courses",courses.get());
        if(error != null) {
            errorMessage = "Invalid Email entered!!";
            modelAndView.addObject("errorMessage", errorMessage);
        }
        return modelAndView;
    }

    @PostMapping("/addStudentToCourse")
    public ModelAndView addStudentToCourse(Model model, @ModelAttribute("person") Person person,
                                           HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        Courses courses = (Courses) session.getAttribute("courses");
        Person personEntity = personRepository.readByEmail(person.getEmail());
        if(personEntity==null || !(personEntity.getPersonId()>0)){
            modelAndView.setViewName("redirect:/admin/viewStudents?id="+courses.getCourseId()
                    +"&error=true");
            return modelAndView;
        }
        personEntity.getCourses().add(courses);
        courses.getPersons().add(personEntity);
        personRepository.save(personEntity);
        session.setAttribute("courses",courses);
        modelAndView.setViewName("redirect:/admin/viewStudents?id="+courses.getCourseId());
        return modelAndView;
    }

    @GetMapping("/deleteStudentFromCourse")
    public ModelAndView deleteStudentFromCourse(Model model, @RequestParam int personId,
                                                HttpSession session) {
        Courses courses = (Courses) session.getAttribute("courses");
        Optional<Person> person = personRepository.findById(personId);
        person.get().getCourses().remove(courses);
        courses.getPersons().remove(person);
        personRepository.save(person.get());
        session.setAttribute("courses",courses);
        ModelAndView modelAndView = new
                ModelAndView("redirect:/admin/viewStudents?id="+courses.getCourseId());
        return modelAndView;
    }

    // ========== LECTURER MANAGEMENT ENDPOINTS ==========

    @GetMapping("/displayLecturers")
    public ModelAndView displayLecturers(Model model,
                                         @RequestParam(required = false) String error,
                                         @RequestParam(required = false) String success) {
        ModelAndView modelAndView = new ModelAndView("lecturers.html");
        List<Person> lecturers = personService.getAllLecturers();
        modelAndView.addObject("lecturers", lecturers);
        modelAndView.addObject("person", new Person());
        if (error != null) {
            modelAndView.addObject("errorMessage", error);
        }
        if (success != null) {
            modelAndView.addObject("successMessage", success);
        }
        return modelAndView;
    }

    @PostMapping("/addNewLecturer")
    public ModelAndView addNewLecturer(@Valid @ModelAttribute("person") Person person,
                                       Errors errors) {
        ModelAndView modelAndView = new ModelAndView();
        if (errors.hasErrors()) {
            modelAndView.setViewName("redirect:/admin/displayLecturers?error=Ju lutem plotesoni te gjitha fushat!");
            return modelAndView;
        }

        Person existingPerson = personRepository.readByEmail(person.getEmail());
        if (existingPerson != null) {
            modelAndView.setViewName("redirect:/admin/displayLecturers?error=Ky email eshte tashme i regjistruar!");
            return modelAndView;
        }

        boolean isSaved = personService.createNewLecturer(person);
        if (isSaved) {
            modelAndView.setViewName("redirect:/admin/displayLecturers?success=Pedagogu u regjistrua me sukses!");
        } else {
            modelAndView.setViewName("redirect:/admin/displayLecturers?error=Ndodhi nje gabim gjate regjistrimit!");
        }
        return modelAndView;
    }

    @GetMapping("/assignLecturer")
    public ModelAndView assignLecturerPage(@RequestParam int courseId, HttpSession session,
                                           @RequestParam(required = false) String error,
                                           @RequestParam(required = false) String success) {
        ModelAndView modelAndView = new ModelAndView("assign_lecturer.html");
        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            modelAndView.setViewName("redirect:/admin/displayCourses");
            return modelAndView;
        }

        Courses course = courseOpt.get();
        List<Person> lecturers = personService.getAllLecturers();

        modelAndView.addObject("course", course);
        modelAndView.addObject("lecturers", lecturers);
        session.setAttribute("currentCourse", course);

        if (error != null) {
            modelAndView.addObject("errorMessage", error);
        }
        if (success != null) {
            modelAndView.addObject("successMessage", success);
        }
        return modelAndView;
    }

    @PostMapping("/assignLecturerToCourse")
    public ModelAndView assignLecturerToCourse(@RequestParam int lecturerId, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        Courses course = (Courses) session.getAttribute("currentCourse");

        if (course == null) {
            modelAndView.setViewName("redirect:/admin/displayCourses");
            return modelAndView;
        }

        Optional<Person> lecturerOpt = personRepository.findById(lecturerId);
        if (lecturerOpt.isEmpty()) {
            modelAndView.setViewName("redirect:/admin/assignLecturer?courseId=" + course.getCourseId() +
                "&error=Pedagogu nuk u gjet!");
            return modelAndView;
        }

        Person lecturer = lecturerOpt.get();
        course.setLecturer(lecturer);
        coursesRepository.save(course);

        modelAndView.setViewName("redirect:/admin/assignLecturer?courseId=" + course.getCourseId() +
            "&success=Pedagogu u caktua me sukses!");
        return modelAndView;
    }

    @GetMapping("/removeLecturerFromCourse")
    public ModelAndView removeLecturerFromCourse(@RequestParam int courseId) {
        ModelAndView modelAndView = new ModelAndView();

        Optional<Courses> courseOpt = coursesRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            modelAndView.setViewName("redirect:/admin/displayCourses");
            return modelAndView;
        }

        Courses course = courseOpt.get();
        course.setLecturer(null);
        coursesRepository.save(course);

        modelAndView.setViewName("redirect:/admin/assignLecturer?courseId=" + courseId +
            "&success=Pedagogu u hoq nga kursi!");
        return modelAndView;
    }

    // ========== ENROLLMENT REQUEST MANAGEMENT ==========

    @GetMapping("/displayEnrollmentRequests")
    public ModelAndView displayEnrollmentRequests(@RequestParam(required = false) String success,
                                                   @RequestParam(required = false) String error) {
        ModelAndView modelAndView = new ModelAndView("enrollment_requests.html");
        List<CourseEnrollmentRequest> pendingRequests = enrollmentRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        modelAndView.addObject("requests", pendingRequests);
        if (success != null) {
            modelAndView.addObject("successMessage", success);
        }
        if (error != null) {
            modelAndView.addObject("errorMessage", error);
        }
        return modelAndView;
    }

    @PostMapping("/approveEnrollmentRequest")
    public String approveEnrollmentRequest(@RequestParam int requestId, RedirectAttributes redirectAttributes) {
        Optional<CourseEnrollmentRequest> requestOpt = enrollmentRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kerkesa nuk u gjet!");
            return "redirect:/admin/displayEnrollmentRequests";
        }

        CourseEnrollmentRequest request = requestOpt.get();
        Person student = request.getStudent();
        Courses course = request.getCourse();

        if ("ENROLL".equals(request.getRequestType())) {
            // Add student to course
            student.getCourses().add(course);
            course.getPersons().add(student);
            personRepository.save(student);
        } else if ("UNENROLL".equals(request.getRequestType())) {
            // Remove student from course
            student.getCourses().remove(course);
            course.getPersons().remove(student);
            personRepository.save(student);
        }

        request.setStatus("APPROVED");
        enrollmentRequestRepository.save(request);

        redirectAttributes.addAttribute("success", "Kerkesa u aprovua me sukses!");
        return "redirect:/admin/displayEnrollmentRequests";
    }

    @PostMapping("/rejectEnrollmentRequest")
    public String rejectEnrollmentRequest(@RequestParam int requestId, RedirectAttributes redirectAttributes) {
        Optional<CourseEnrollmentRequest> requestOpt = enrollmentRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            redirectAttributes.addAttribute("error", "Kerkesa nuk u gjet!");
            return "redirect:/admin/displayEnrollmentRequests";
        }

        CourseEnrollmentRequest request = requestOpt.get();
        request.setStatus("REJECTED");
        enrollmentRequestRepository.save(request);

        redirectAttributes.addAttribute("success", "Kerkesa u refuzua!");
        return "redirect:/admin/displayEnrollmentRequests";
    }

}
