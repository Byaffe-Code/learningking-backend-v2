package com.byaffe.learningking.controllers.admin;

import com.byaffe.learningking.controllers.dtos.ArticlesFilterDTO;
import com.byaffe.learningking.dtos.courses.LectureRequestDTO;
import com.byaffe.learningking.dtos.courses.LectureResponseDTO;
import com.byaffe.learningking.models.courses.CourseLecture;
import com.byaffe.learningking.services.CourseSubTopicService;
import com.byaffe.learningking.services.impl.CourseServiceImpl;
import com.byaffe.learningking.shared.api.BaseResponse;
import com.byaffe.learningking.shared.api.ResponseList;
import com.byaffe.learningking.shared.api.ResponseObject;
import com.byaffe.learningking.shared.constants.RecordStatus;
import com.googlecode.genericdao.search.Search;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ray Gdhrt
 */
@Slf4j
@RestController
@RequestMapping("api/v1/admin/lectures")
public class AdminLecturesController {
@Autowired
    ModelMapper modelMapper;

@Autowired
CourseSubTopicService modelService;

    @PostMapping("")
    public ResponseEntity<BaseResponse> saveAndUpdate(@RequestBody LectureRequestDTO dto) throws JSONException {
       modelService.saveInstance(dto);
        return ResponseEntity.ok().body(new BaseResponse(true));
    }
    @PostMapping(path = "/multipart", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<BaseResponse> saveAndUpdateV2(@RequestPart @Valid LectureRequestDTO dto, @RequestPart("file") MultipartFile file) throws JSONException {
       dto.setCoverImage(file);
        modelService.saveInstance(dto);
        return ResponseEntity.ok().body(new BaseResponse(true));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ResponseObject<LectureResponseDTO>> getById(@PathVariable(name = "id") long id) throws JSONException {
        CourseLecture course=modelService.getInstanceByID(id);
        return ResponseEntity.ok().body(new ResponseObject<>(modelMapper.map(course, LectureResponseDTO.class)));

    }
    @DeleteMapping("/{id}/delete")
    public ResponseEntity<BaseResponse> deleteCourse(@PathVariable long id) throws JSONException {
        CourseLecture course=modelService.getInstanceByID(id);
        modelService.deleteInstance(course);
        return ResponseEntity.ok().body(new BaseResponse(true));
    }
    @GetMapping("")
    public ResponseEntity<ResponseList<LectureResponseDTO>> getRecords(ArticlesFilterDTO queryParamModel) throws JSONException {

        Search search = CourseServiceImpl.generateSearchObjectForCourses(queryParamModel.getSearchTerm())
                .addFilterEqual("recordStatus", RecordStatus.ACTIVE);

        if (queryParamModel.getSortBy() != null) {
            search.addSort(queryParamModel.getSortBy(), queryParamModel.getSortDescending());
        }
        List<CourseLecture> courses = modelService.getInstances(search, queryParamModel.getOffset(), queryParamModel.getLimit());
        long count = modelService.countInstances(search);
        return ResponseEntity.ok().body(new ResponseList<>(courses.stream().map(r->modelMapper.map(r,LectureResponseDTO.class)).collect(Collectors.toList()), (int) count, queryParamModel.getOffset(), queryParamModel.getLimit()));

    }

    @GetMapping("/v2/{id}")
    public ResponseEntity<ResponseObject<LectureResponseDTO>> getById(@PathVariable("id") Long id) throws JSONException {
         CourseLecture course = modelService.getInstanceByID(id);
        return ResponseEntity.ok().body(new ResponseObject<>(modelMapper.map(course, LectureResponseDTO.class)));
    }



}
