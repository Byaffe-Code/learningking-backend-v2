package com.byaffe.learningking.controllers;

import com.byaffe.learningking.controllers.constants.ApiUtils;
import com.byaffe.learningking.controllers.dtos.*;
import com.byaffe.learningking.models.Student;
import com.byaffe.learningking.models.courses.*;
import com.byaffe.learningking.services.*;
import com.byaffe.learningking.services.impl.CourseServiceImpl;
import com.byaffe.learningking.shared.api.ResponseList;
import com.byaffe.learningking.shared.api.ResponseObject;
import com.byaffe.learningking.shared.constants.RecordStatus;
import com.byaffe.learningking.shared.exceptions.ValidationFailedException;
import com.byaffe.learningking.shared.security.UserDetailsContext;
import com.byaffe.learningking.shared.utils.ApplicationContextProvider;
import com.googlecode.genericdao.search.Search;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ray Gdhrt
 */
@Slf4j
@RestController
@RequestMapping("/v1/courses")
public class CoursesController {

    @GetMapping("")
    public ResponseEntity<ResponseList<CourseResponseDTO>> getCourses(@RequestParam ArticlesFilterDTO queryParamModel) throws JSONException {

        Search search = CourseServiceImpl.generateSearchObjectForCourses(queryParamModel.getSearchTerm())
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE)
                .addFilterEqual("publicationStatus", PublicationStatus.ACTIVE);
        if (queryParamModel.getCategoryId() != null) {
            search.addFilterEqual("category.id", queryParamModel.getCategoryId());
        }
        if (queryParamModel.getAuthorId() != null) {
            search.addFilterEqual("instructor.id", queryParamModel.getAuthorId());
        }

        if (queryParamModel.getFeatured() != null) {
            search.addFilterEqual("isFeatured", queryParamModel.getFeatured());
        }

        if (queryParamModel.getSortBy() != null) {
            search.addSort(queryParamModel.getSortBy(), queryParamModel.getSortDescending());
        }
        List<CourseResponseDTO> courses = new ArrayList<>();
        for (Course course : ApplicationContextProvider.getBean(CourseService.class).getInstances(search, queryParamModel.getOffset(), queryParamModel.getLimit())) {

            CourseResponseDTO dto = (CourseResponseDTO) course;

            int lessonsCount = ApplicationContextProvider.getBean(CourseLessonService.class)
                    .countInstances(new Search()
                            .addFilterEqual("course", course)
                            .addFilterEqual("recordStatus", RecordStatus.ACTIVE));
            double rattings = 0;

            try {
                rattings = ApplicationContextProvider.getBean(CourseRatingService.class).getTotalCourseRatings(course) / 5;
            } catch (Exception e) {
                e.printStackTrace();
            }
            CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(UserDetailsContext.getLoggedInStudent(), course);

            dto.setEnrolled(subscription != null);
            dto.setNumberOfLessons(lessonsCount);
            dto.setAverageRating((rattings / 5));
            dto.setRatingsCount(ApplicationContextProvider.getBean(CourseRatingService.class).getRatingsCount(course));

            courses.add(dto);
        }
        return ResponseEntity.ok().body(new ResponseList<>(courses, (int) 0, queryParamModel.getOffset(), queryParamModel.getLimit()));

    }


    @GetMapping("/by-categories")
    public ResponseEntity<ResponseList<CourseByTopicResponseDTO>> getCoursesByCategories(@RequestParam ArticlesFilterDTO queryParamModel) throws JSONException {
        List<CourseByTopicResponseDTO> records = new ArrayList<>();
        for (CourseCategory devTopic : ApplicationContextProvider.getBean(CourseCategoryService.class).getInstances(new Search().addFilterEqual("recordStatus", RecordStatus.ACTIVE), 0, 0)) {
            List<Course> coursesModels = ApplicationContextProvider.getBean(CourseService.class).getInstances(new Search()
                    .addFilterEqual("recordStatus", RecordStatus.ACTIVE)
                    .addFilterEqual("publicationStatus", PublicationStatus.ACTIVE)
                    .addFilterEqual("category", devTopic), queryParamModel.getOffset(), queryParamModel.getLimit());

            CourseByTopicResponseDTO courseByTopicResponseDTO = (CourseByTopicResponseDTO) devTopic;
            List<CourseResponseDTO> dtos = new ArrayList<>();
            for (Course course : coursesModels) {
                CourseResponseDTO dto = (CourseResponseDTO) course;
                int lessonsCount = ApplicationContextProvider.getBean(CourseLessonService.class)
                        .countInstances(new Search().addFilterEqual("course", course).addFilterEqual("recordStatus", RecordStatus.ACTIVE));
                double rattings = 0;
                try {
                    rattings = ApplicationContextProvider.getBean(CourseRatingService.class).getTotalCourseRatings(course) / 5;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(UserDetailsContext.getLoggedInStudent(), course);
                dto.setEnrolled(subscription != null);
                dto.setNumberOfLessons(lessonsCount);
                dto.setAverageRating((rattings / 5));
                dto.setRatingsCount(ApplicationContextProvider.getBean(CourseRatingService.class).getRatingsCount(course));
                dtos.add(dto);
            }
            courseByTopicResponseDTO.setCourses(dtos);
            records.add(courseByTopicResponseDTO);
        }
        return ResponseEntity.ok().body(new ResponseList<>(records, (int) 0, queryParamModel.getOffset(), queryParamModel.getLimit()));

    }


    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<CourseDetailsResponseDTO>> getCourseDetails(@PathVariable("id") Long id) throws JSONException {
        Student member = new Student();
        List<LessonResponseDTO> lessonsArray = new ArrayList<>();
        CourseService courseService = ApplicationContextProvider.getBean(CourseService.class);
        Course course = courseService.getInstanceByID(id);
        CourseDetailsResponseDTO responseDTO = new CourseDetailsResponseDTO();
        CourseResponseDTO courseObj = (CourseResponseDTO) course;
        List<CourseLesson> lessons = ApplicationContextProvider.getBean(CourseLessonService.class).getInstances(new Search()
                .addFilterEqual("course", course)
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE), 0, 0);
        CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(member, course);

        for (CourseLesson lesson : lessons) {
            LessonResponseDTO lessonDto = (LessonResponseDTO) lesson;

            lessonDto.setProgress(ApplicationContextProvider.getBean(CourseLessonService.class
            ).getProgress(subscription.getCurrentSubTopic()));
            lessonDto.setIsPreview(lesson.getPosition() == 1);
            lessonsArray.add(lessonDto);
        }

        double rattings = 1;
        try {
            rattings = ApplicationContextProvider.getBean(CourseRatingService.class).getTotalCourseRatings(course) / 5;
        } catch (Exception e) {
            e.printStackTrace();
        }

        courseObj.setEnrolled(subscription != null);
        courseObj.setAverageRating(rattings / 5);
        courseObj.setProgress(courseService.getProgress(subscription.getCurrentSubTopic()));
        //     .put("ratingsCount", ApplicationContextProvider.getBean(CourseRatingService.class).getRatingsCount(course))
        courseObj.setTestimonials(course.getTestimonials());
        responseDTO.setSubscription(subscription);
        responseDTO.setLessons(lessonsArray.stream().map(r -> (LessonResponseDTO) r).collect(Collectors.toList()));
        responseDTO.setNumberOfLessons(lessons.size());
        return ResponseEntity.ok().body(new ResponseObject<>(responseDTO));


    }


    @GetMapping("/lessons/{id}")
    public ResponseEntity<ResponseObject<LessonResponseDTO>> getLessons(@PathVariable("id") Long id) throws JSONException {
        LessonResponseDTO result = new LessonResponseDTO();
        Student member = new Student();

        CourseLesson lesson = ApplicationContextProvider.getBean(CourseLessonService.class).getInstanceByID(id);
        if (lesson == null) {
            throw new ValidationFailedException("Lesson not found");
        }
        result = (LessonResponseDTO) lesson;
        CourseTopicService courseSubTopicService = ApplicationContextProvider.getBean(CourseTopicService.class);
        List<CourseTopic> topics = courseSubTopicService.getInstances(new Search()
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE)
                .addFilterEqual("courseLesson", lesson), 0, 0);
        CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(member, lesson.getCourse());

        for (CourseTopic topic : topics) {
            CourseTopicResponseDTO topicJSONObject = (CourseTopicResponseDTO) topic;
            topicJSONObject.setProgress(courseSubTopicService.getProgress(subscription.getCurrentSubTopic()));
            result.getTopics().add(topicJSONObject);
        }
        result.setIsPreview(lesson.getPosition() == 1);
        result.setProgress(ApplicationContextProvider.getBean(CourseLessonService.class).getProgress(subscription.getCurrentSubTopic()));
        return ResponseEntity.ok().body(new ResponseObject<>(result));
    }


    @GetMapping("/topic/{id}")
    public ResponseEntity<ResponseObject<CourseTopicResponseDTO>> getTopicById(@PathVariable("id") Long id) throws JSONException {
        CourseTopicResponseDTO result = new CourseTopicResponseDTO();
        Student member = new Student();

        CourseTopic topic = ApplicationContextProvider.getBean(CourseTopicService.class).getInstanceByID(id);
        if (topic == null) {
            throw new ValidationFailedException("Topic not found");
        }
        List<CourseLecture> subTopics = ApplicationContextProvider.getBean(CourseSubTopicService.class
        ).getInstances(new Search()
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE)
                .addFilterEqual("courseTopic", topic), 0, 0);
        CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(member, topic.getCourseLesson().getCourse());

        for (CourseLecture subTopic : subTopics) {
            CourseLectureResponseDTO jSONObject = (CourseLectureResponseDTO) (subTopic);
            result.getSubTopics().add(jSONObject);
        }
        result.setProgress(ApplicationContextProvider.getBean(CourseTopicService.class).getProgress(subscription.getCurrentSubTopic()));
        return ResponseEntity.ok().body(new ResponseObject<>(result));

    }


    @PostMapping("/enroll/{id}")
    public ResponseEntity<ResponseObject<EnrollCourseResponseDTO>> enroll(@PathVariable("id") Long id) throws JSONException {
        EnrollCourseResponseDTO result = new EnrollCourseResponseDTO();
        Student member = UserDetailsContext.getLoggedInStudent();
        CourseService courseService = ApplicationContextProvider.getBean(CourseService.class);
        Course courseSerie = courseService.getInstanceByID(id);
        if (courseSerie == null) {
            throw new ValidationFailedException("Course Not Found");
        }
        CourseSubscription courseSubscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).createSubscription(member, courseSerie);
        result.setSubscription(courseSubscription);
        result.setCourse(courseSerie);
        return ResponseEntity.ok().body(new ResponseObject<>(result));

    }


    @PostMapping("/rating")
    public ResponseEntity<ResponseObject<CourseRating>> rateCourse(@RequestBody CourseRatingDTO courseRatingDTO) throws JSONException {
        Student member = UserDetailsContext.getLoggedInStudent();
        CourseService courseService = ApplicationContextProvider.getBean(CourseService.class);
        Course course = courseService.getInstanceByID(courseRatingDTO.getCourseId());
        CourseRating courseRating = new CourseRating();
        courseRating.setCourse(course);
        courseRating.setStudent(member);
        courseRating.setReviewText(courseRatingDTO.getRatingText());
        courseRating.setStarsCount(courseRatingDTO.getStars());
        courseRating = ApplicationContextProvider.getBean(CourseRatingService.class).saveInstance(courseRating);
        return ResponseEntity.ok().body(new ResponseObject<>(courseRating));
    }


    @GetMapping("/rating/{courseId}")
    public ResponseEntity<ResponseList<CourseRatingResponseDTO>> getRatings(@PathVariable("courseId") Long courseId) throws JSONException {

        List<CourseRating> courseRatings = ApplicationContextProvider.getBean(CourseRatingService.class)
                .getInstances(new Search().
                                addFilterEqual("course.id", courseId).
                                addFilterEqual("recordStatus", RecordStatus.ACTIVE).
                                addFilterEqual("publicationStatus", PublicationStatus.ACTIVE),
                        0, 0);

        List<CourseRatingResponseDTO> ratings = new ArrayList<>();
        for (CourseRating courseRating : courseRatings) {
            CourseRatingResponseDTO dto = new CourseRatingResponseDTO();
            dto.setStars(courseRating.getStarsCount());
            dto.setDateCreated(ApiUtils.ENGLISH_DATE_FORMAT.format(courseRating.getDateCreated()));
            dto.setMemberFullName(courseRating.getStudent().getFullName());
            dto.setRatingText(courseRating.getReviewText());
            ratings.add(dto);

        }
        return ResponseEntity.ok().body(new ResponseList<>(ratings, 0, 0, 0));

    }


    @PostMapping("/subtopics/complete/{id}")
    public ResponseEntity<CourseSubscription> completeSubTopic(@PathVariable("id") Long id) throws JSONException {
        Student member = UserDetailsContext.getLoggedInStudent();
        CourseLecture topic = ApplicationContextProvider.getBean(CourseSubTopicService.class).getInstanceByID(id);
        if (topic == null) {
            throw new ValidationFailedException("Topic  Not Found");
        }
        CourseSubscription courseSubscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).completeSubTopic(member, topic);

        return ResponseEntity.ok().body(courseSubscription);
    }


    @GetMapping("/mycourses")
    public ResponseEntity<List<CourseResponseDTO>> getStudentCourses(@RequestParam ArticlesFilterDTO queryParamModel) throws JSONException {

        Search search = CourseServiceImpl.generateSearchObjectForCourses(queryParamModel.getSearchTerm())
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        if (queryParamModel.getCategoryId() != null) {
            search.addFilterEqual("course.category.id", queryParamModel.getCategoryId());
        }
        if (queryParamModel.getAuthorId() != null) {
            search.addFilterEqual("course.instructor.id", queryParamModel.getAuthorId());
        }

        if (queryParamModel.getFeatured() != null) {
            search.addFilterEqual("course.isFeatured", queryParamModel.getFeatured());
        }

        if (queryParamModel.getSortBy() != null) {
            search.addSort(queryParamModel.getSortBy(), queryParamModel.getSortDescending());
        }
        List<CourseResponseDTO> courses = new ArrayList<>();
        for (CourseSubscription course : ApplicationContextProvider.getBean(CourseSubscriptionService.class).getInstances(search, queryParamModel.getOffset(), queryParamModel.getLimit())) {

            CourseResponseDTO dto = (CourseResponseDTO) course.getCourse();

            int lessonsCount = ApplicationContextProvider.getBean(CourseLessonService.class)
                    .countInstances(new Search()
                            .addFilterEqual("course", course.getCourse())
                            .addFilterEqual("recordStatus", RecordStatus.ACTIVE));
            double rattings = 0;

            try {
                rattings = ApplicationContextProvider.getBean(CourseRatingService.class).getTotalCourseRatings(course.getCourse()) / 5;
            } catch (Exception e) {
                e.printStackTrace();
            }
            CourseSubscription subscription = ApplicationContextProvider.getBean(CourseSubscriptionService.class).getSerieSubscription(UserDetailsContext.getLoggedInStudent(), course.getCourse());

            dto.setEnrolled(subscription != null);
            dto.setNumberOfLessons(lessonsCount);
            dto.setAverageRating((rattings / 5));
            dto.setRatingsCount(ApplicationContextProvider.getBean(CourseRatingService.class).getRatingsCount(course.getCourse()));

            courses.add(dto);
        }
        return ResponseEntity.ok().body((courses));


    }


    @GetMapping("/categories")
    public ResponseEntity<JSONObject> getTopics(@RequestParam ArticlesFilterDTO queryParamModel) throws JSONException {
        JSONObject result = new JSONObject();

        Search search = new Search().addFilterEqual("recordStatus", RecordStatus.ACTIVE);
        if (StringUtils.isNotBlank(queryParamModel.getSortBy())) {
            search.addSort(queryParamModel.getSortBy(), queryParamModel.getSortDescending());
        }
        if (StringUtils.isNotBlank(queryParamModel.getType())) {
            CourseAcademyType academyType = CourseAcademyType.valueOf(queryParamModel.getType());
            search.addFilterEqual("academy", academyType);
        }
        JSONArray topics = new JSONArray();
        for (CourseCategory topic : ApplicationContextProvider.getBean(CourseCategoryService.class).getInstances(search, queryParamModel.getOffset(), queryParamModel.getLimit())) {
            int count = ApplicationContextProvider.getBean(CourseService.class).countInstances(new Search().addFilterEqual("recordStatus", RecordStatus.ACTIVE).addFilterEqual("category", topic));
            topics.put(
                    new JSONObject()
                            .put("id", topic.getId())
                            .put("academy", topic.getAcademy())
                            .put("name", topic.getName())
                            .put("colorCode", topic.getColorCode())
                            .put("imageUrl", topic.getImageUrl())
                            .put("coursesCount", count)
            );
        }
        //topics= ApiUtils.sortJsonArray(topics, "seriesCount",false);

        result.put("courseCategories", topics);
        return ResponseEntity.ok().body((result));
    }

}
