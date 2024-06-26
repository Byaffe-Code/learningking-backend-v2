package com.byaffe.learningking.services.impl;

import com.byaffe.learningking.models.Student;
import com.byaffe.learningking.models.ReadStatus;
import com.byaffe.learningking.models.courses.Certification;
import com.byaffe.learningking.models.courses.CertificationCourse;
import com.byaffe.learningking.models.courses.CertificationSubscription;
import com.byaffe.learningking.models.courses.Course;
import com.byaffe.learningking.services.CertificationCourseDao;
import com.byaffe.learningking.services.CertificationService;
import com.byaffe.learningking.services.CertificationSubscriptionService;
import com.byaffe.learningking.shared.constants.RecordStatus;
import com.byaffe.learningking.shared.dao.BaseDAOImpl;
import com.byaffe.learningking.shared.exceptions.OperationFailedException;
import com.byaffe.learningking.shared.exceptions.ValidationFailedException;
import com.googlecode.genericdao.search.Search;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CertificationSubscriptionServiceImpl extends BaseDAOImpl<CertificationSubscription> implements CertificationSubscriptionService {

    @Autowired
    CertificationService certificationService;

    @Autowired
    CertificationCourseDao certificationCourseDao;

    @Override
    public CertificationSubscription getInstanceByID(Long id) {
        return null;
    }

    @Override
    public CertificationSubscription saveInstance(CertificationSubscription subscription) throws ValidationFailedException {
        CertificationSubscription exists = getSubscription(subscription.getStudent(), subscription.getCertification());

        if (exists != null && !exists.getId().equals(subscription.getId())) {
            subscription.setId(exists.getId());
            subscription.setCertification(exists.getCertification());
            subscription.setCompletedCourses(exists.getCompletedCourses());
        }

        return super.merge(subscription);
    }

    @Override
    public CertificationSubscription createSubscription(Certification course, Student student) {
        CertificationSubscription exists = getSubscription(student, course);

        if (exists == null) {

            CertificationSubscription subscription = new CertificationSubscription();
            subscription.setCertification(course);
            subscription.setStudent(student);
            subscription.setCompletedCourses(1);
            return super.merge(subscription);
        }
        return exists;
    }

    @Override
    public List<CertificationSubscription> getSubscriptions(Student student) {
        return super.searchByPropertyEqual("member", student, RecordStatus.ACTIVE); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<CertificationSubscription> getInstances(Search search, int offset, int limit) {
        if (search == null) {
            search = new Search();
        }
        return super.search(search.setFirstResult(offset).setMaxResults(limit)); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteInstance(CertificationSubscription memberPlan) {
        memberPlan.setRecordStatus(RecordStatus.DELETED);
        super.save(memberPlan);//To change body of generated methods, choose Tools | Templates.
    }


    public CertificationSubscription getInstanceByID(String member_plan_id) {
        if (member_plan_id == null) {
            return null;
        }
        return super.searchUniqueByPropertyEqual("id", member_plan_id, RecordStatus.ACTIVE);

    }

    @Override
    public CertificationSubscription createSubscription(Student student, Certification course) throws ValidationFailedException {

        if (course.isPaid() || course.getCost() > 0) {
            throw new ValidationFailedException("This is a paid Certification");
        }
        return createActualSubscription(student, course);
    }

    private CertificationSubscription createActualSubscription(Student student, Certification course) {

        CertificationSubscription courseSubscription = new CertificationSubscription();
        courseSubscription.setStudent(student);
        courseSubscription.setCertification(course);
        courseSubscription.setReadStatus(ReadStatus.Inprogress);
        return super.save(courseSubscription);
    }

    @Override
    public CertificationSubscription getSubscription(Student student, Certification course) {
        if (student == null || course == null) {
            return null;
        }

        Search search = new Search().addFilterEqual("member", student)
                .addFilterEqual("certification", course);
        try {
            return super.searchUnique(search);
        } catch (javax.persistence.NonUniqueResultException e) {
            System.err.println("Error ocurred...\n" + e.getLocalizedMessage());
            return (CertificationSubscription) super.search(search).get(0);
        }

    }

    @Override
    public int countInstances(Search search) {
        return super._count(search);
    }

    @Override
    public void deleteInstances(Search search) throws OperationFailedException {
        // super.delete(entity);
    }

    @Override
    public void completeCertificationCourse(Student student, Course course) throws ValidationFailedException {
        if (course == null || course.isNew()) {
            throw new ValidationFailedException("Missing course");
        }
        List<CertificationSubscription> certificationSubscriptions = getSubscriptions(student);

        if (certificationSubscriptions != null &&! certificationSubscriptions.isEmpty()) {
           
        for (CertificationSubscription certificationSubscription : certificationSubscriptions) {
            List<CertificationCourse> certificationCourses = certificationCourseDao.searchUnique(
                    new Search()
                            .addFilterEqual("certification", certificationSubscription.getCertification())
                            .addFilterEqual("course", course)
            );

            if (certificationCourses != null && !certificationCourses.isEmpty()) {

                int courseCount = certificationCourseDao.count(new Search().addFilterEqual("certification", certificationSubscription.getCertification()));
                certificationSubscription.setCompletedCourses(certificationSubscription.getCompletedCourses() + 1);
                if (certificationSubscription.getCompletedCourses() >= courseCount) {
                    certificationSubscription.setReadStatus(ReadStatus.Completed);
                } else {
                    certificationSubscription.setReadStatus(ReadStatus.Inprogress);
                }
                super.save(certificationSubscription);
            }

        }
        }
    }



}
