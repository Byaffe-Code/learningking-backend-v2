package com.byaffe.learningking.services.impl;

import com.byaffe.learningking.constants.TransactionStatus;
import com.byaffe.learningking.models.Member;
import com.byaffe.learningking.models.ReadStatus;
import com.byaffe.learningking.models.courses.*;
import com.byaffe.learningking.models.payments.CoursePayment;
import com.byaffe.learningking.models.payments.MemberSubscriptionPlan;
import com.byaffe.learningking.services.*;
import com.byaffe.learningking.shared.constants.RecordStatus;
import com.byaffe.learningking.shared.dao.BaseDAOImpl;
import com.byaffe.learningking.shared.exceptions.OperationFailedException;
import com.byaffe.learningking.shared.exceptions.ValidationFailedException;
import com.byaffe.learningking.shared.utils.ApplicationContextProvider;
import com.googlecode.genericdao.search.Search;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CourseSubscriptionServiceImpl extends BaseDAOImpl<CourseSubscription> implements CourseSubscriptionService {

    @Autowired
    CourseService courseService;

    @Autowired
    CourseLessonService courseLessonService;

    @Autowired
    CourseTopicService courseTopicService;

    @Autowired
    CourseSubTopicService courseSubTopicService;

    @Override
    public CourseSubscription saveInstance(CourseSubscription subscription) throws ValidationFailedException {
        CourseSubscription exists = getSerieSubscription(subscription.getMember(), subscription.getCourse());

        if (exists != null && !exists.getId().equals(subscription.getId())) {
            subscription.setId(exists.getId());
            subscription.setCurrentLesson(exists.getCurrentLesson());
            subscription.setCurrentTopic(exists.getCurrentTopic());
        }

        return super.merge(subscription);
    }

    @Override
    public CourseSubscription createSubscription(Course course, Member member) {
        CourseSubscription exists = getSerieSubscription(member, course);

        if (exists == null) {

            CourseSubscription subscription = new CourseSubscription();
            subscription.setCourse(course);
            subscription.setMember(member);
            subscription.setCurrentLesson(1);
            subscription.setCurrentTopic(1);
            return super.merge(subscription);
        }
        return exists;
    }

    @Override
    public List<CourseSubscription> getPlansForMember(Member member) {
        return super.searchByPropertyEqual("member", member, RecordStatus.ACTIVE); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<CourseSubscription> getInstances(Search search, int offset, int limit) {
        if (search == null) {
            search = new Search();
        }
        return super.search(search.setFirstResult(offset).setMaxResults(limit)); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteInstance(CourseSubscription memberPlan) {
        memberPlan.setRecordStatus(RecordStatus.DELETED);
        super.save(memberPlan);//To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public CourseSubscription getInstanceByID(Long member_plan_id) {
        if (member_plan_id == null) {
            return null;
        }
        return super.searchUniqueByPropertyEqual("id", member_plan_id, RecordStatus.ACTIVE);

    }

    @Override
    public CourseSubscription createSubscription(Member member, Course course) throws ValidationFailedException {

        if (course.isIsPaid() || course.getCost() > 0) {
            throw new ValidationFailedException("This is a paid Course");
        }
        return createActualSubscription(member, course);
    }

    private CourseSubscription createActualSubscription(Member member, Course course) {
        CourseSubscription courseSubscription = new CourseSubscription();
        CourseSubTopic firstSubTopic = null;
        try {
            firstSubTopic = ApplicationContextProvider.getBean(CourseService.class).getFirstSubTopic(course);
        } catch (ValidationFailedException ex) {
            courseSubscription.setLastErrorMessage(ex.getMessage());
        } finally {
            courseSubscription.setMember(member);
            courseSubscription.setCourse(course);
            courseSubscription.setCurrentSubTopic(firstSubTopic);
            courseSubscription.setCurrentTopic(1);
            courseSubscription.setCurrentLesson(1);
            courseSubscription.setReadStatus(ReadStatus.Inprogress);
            return super.save(courseSubscription);
        }
    }

    @Override
    public CourseSubscription createActualSubscription(Course course, MemberSubscriptionPlan memberSubscriptionPlan) throws ValidationFailedException {
        CourseSubscription courseSubscription = new CourseSubscription();
        CourseSubTopic firstSubTopic = null;
        try {
            firstSubTopic = ApplicationContextProvider.getBean(CourseService.class).getFirstSubTopic(course);
        } catch (ValidationFailedException ex) {
            courseSubscription.setLastErrorMessage(ex.getMessage());
        } finally {
            courseSubscription.setMember(memberSubscriptionPlan.getMember());
            courseSubscription.setMemberSubscriptionPlan(memberSubscriptionPlan);
            courseSubscription.setCourse(course);
            courseSubscription.setCurrentSubTopic(firstSubTopic);
            courseSubscription.setCurrentTopic(1);
            courseSubscription.setCurrentLesson(1);
            courseSubscription.setReadStatus(ReadStatus.Inprogress);
            return super.save(courseSubscription);
        }
    }

    @Override
    public CourseSubscription createSubscription(CoursePayment coursePayment) throws ValidationFailedException {
        if (coursePayment == null || !coursePayment.getStatus().equals(TransactionStatus.SUCESSFULL)) {
            return null;
        }
        return createActualSubscription(coursePayment.getSubscriber(), coursePayment.getCourse());
    }

    @Override
    public CourseSubscription getSerieSubscription(Member member, Course course) {
        if (member == null || course == null) {
            return null;
        }

        Search search = new Search().addFilterEqual("member", member)
                .addFilterEqual("course", course);
        try {
            return super.searchUnique(search);
        } catch (javax.persistence.NonUniqueResultException e) {
            System.err.println("Error ocurred...\n" + e.getLocalizedMessage());
            return (CourseSubscription) super.search(search).get(0);
        }

    }

    @Override
    public int countInstances(Search search) {
        return super.count(search);
    }

    @Override
    public void deleteInstances(Search search) throws OperationFailedException {
        // super.delete(entity);
    }

    @Override
    public CourseSubscription completeSubTopic(Member member, CourseSubTopic subTopic) throws ValidationFailedException {
        if (subTopic == null || subTopic.isNew()) {
            throw new ValidationFailedException("Missing subTopic");
        }
        CourseSubscription courseSubscription = getSerieSubscription(member, subTopic.getCourseTopic().getCourseLesson().getCourse());

        if (courseSubscription == null) {
            throw new ValidationFailedException("Not enrolled for this course");
        }

        if (courseSubscription.getCurrentSubTopic().getCourseTopic() != subTopic.getCourseTopic()) {

            throw new ValidationFailedException("Your previous topic hasnt been completed yet. Please complete all the subtopics in it");

        }

        if (subTopic.getPosition() > courseSubscription.getCurrentSubTopic().getPosition()) {
            throw new ValidationFailedException("You can't skip previous subtopics. Please complete all the subtopics in order");

        }

        List<CourseSubTopic> subTopics = courseSubTopicService.getInstances(
                new Search()
                        .addSortAsc("position")
                        .addFilterEqual("courseTopic", subTopic.getCourseTopic())
                        .addFilterGreaterOrEqual("position", subTopic.getPosition()), 0, 1);

        //If subtopic of higher position exists in topic 
        if (!subTopics.isEmpty()) {
            courseSubscription.setCurrentSubTopic(subTopics.get(0));
            courseSubscription.setCurrentTopic(subTopic.getCourseTopic().getPosition());
            courseSubscription = saveInstance(courseSubscription);
            return courseSubscription;
        }

        //look for next topic
        List<CourseTopic> topics = courseTopicService.getInstances(
                new Search()
                        .addFilterGreaterOrEqual("position", subTopic.getCourseTopic().getPosition() + 1)
                        .addFilterEqual("courseLesson", subTopic.getCourseTopic().getCourseLesson())
                        .addSortAsc("position"), 0, 1);
        CourseTopic nextTopic = topics.get(0);

        //Next topic exists on lesson
        if (nextTopic != null) {
            //get first subtopic for this next topic

            CourseSubTopic nextSubTopic = courseSubTopicService.getFirstSubTopic(nextTopic);
            courseSubscription.setCurrentSubTopic(nextSubTopic);
            courseSubscription = saveInstance(courseSubscription);
            return courseSubscription;

        }
        // Fetch next lesson
        List<CourseLesson> lessons = courseLessonService.getInstances(
                new Search()
                        .addFilterEqual("position", subTopic.getCourseTopic().getCourseLesson().getPosition() + 1)
                        .addFilterEqual("course", subTopic.getCourseTopic().getCourseLesson().getCourse())
                        .addSortAsc("position"), 0, 1);
        CourseLesson nextLesson = lessons.get(0);

        //Next lessson exists
        if (nextLesson != null) {
            //Fetch first sub-topic in next lesson
            CourseSubTopic nextSubTopicInLesson = courseSubTopicService.getFirstSubTopic(nextLesson);
            courseSubscription.setCurrentSubTopic(nextSubTopicInLesson);
            courseSubscription = saveInstance(courseSubscription);
            return courseSubscription;

        }
        //Next lesson doesnt exist. So lets just complete the course
        courseSubscription.setReadStatus(ReadStatus.Completed);
        courseSubscription = saveInstance(courseSubscription);

        //Update certification progress too
        ApplicationContextProvider.getBean(CertificationSubscriptionService.class).completeCertificationCourse(member, courseSubscription.getCourse());
        return courseSubscription;

    }



}
