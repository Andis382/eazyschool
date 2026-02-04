package com.eazybytes.eazyschool.controller;

import com.eazybytes.eazyschool.model.*;
import com.eazybytes.eazyschool.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpSession;
import java.util.*;

@Slf4j
@Controller
public class CourseController {

    @Autowired
    private CoursesRepository coursesRepository;

    @Autowired
    private CourseRatingRepository courseRatingRepository;

    @Autowired
    private PersonRepository personRepository;

    @GetMapping("/courses")
    public ModelAndView displayCourses() {
        ModelAndView modelAndView = new ModelAndView("courses.html");
        List<Courses> courses = coursesRepository.findAll();

        // Calculate average ratings for each course
        Map<Integer, Double> courseRatings = new HashMap<>();
        for (Courses course : courses) {
            Double avgRating = courseRatingRepository.getAverageRatingByCourse(course);
            courseRatings.put(course.getCourseId(), avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        }

        modelAndView.addObject("courses", courses);
        modelAndView.addObject("courseRatings", courseRatings);
        return modelAndView;
    }
}
