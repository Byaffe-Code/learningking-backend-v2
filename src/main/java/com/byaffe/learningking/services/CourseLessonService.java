package com.byaffe.learningking.services;

import com.byaffe.learningking.models.courses.Course;
import com.byaffe.learningking.models.courses.CourseLesson;
import com.byaffe.learningking.models.courses.CourseSubTopic;

public interface CourseLessonService extends GenericService<CourseLesson> {

    /**
     *
     * @param course
     * @return
     */
    public CourseLesson getFirstLesson(Course course);
 public float getProgress(CourseSubTopic currentSubTopic);
}