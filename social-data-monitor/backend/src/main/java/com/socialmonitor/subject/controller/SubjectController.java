package com.socialmonitor.subject.controller;

import com.socialmonitor.common.response.ApiResponse;
import com.socialmonitor.subject.dto.CreateSubjectRequest;
import com.socialmonitor.subject.dto.SubjectBilibiliBindingView;
import com.socialmonitor.subject.dto.SubjectLayoutItemRequest;
import com.socialmonitor.subject.dto.SubjectTrendView;
import com.socialmonitor.subject.dto.SubjectView;
import com.socialmonitor.subject.dto.SubjectWidgetLayoutView;
import com.socialmonitor.subject.dto.SubjectWorkbenchView;
import com.socialmonitor.subject.dto.UpdateSubjectBilibiliBindingRequest;
import com.socialmonitor.subject.dto.UpdateSubjectRequest;
import com.socialmonitor.subject.service.SubjectService;
import com.socialmonitor.subject.service.SubjectWorkbenchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects")
@ConditionalOnProperty(prefix = "app.subject-monitor", name = "enabled", matchIfMissing = true)
public class SubjectController {

    private final SubjectService subjectService;
    private final SubjectWorkbenchService workbenchService;

    public SubjectController(SubjectService subjectService, SubjectWorkbenchService workbenchService) {
        this.subjectService = subjectService;
        this.workbenchService = workbenchService;
    }

    @GetMapping
    public ApiResponse<List<SubjectView>> subjects() {
        return ApiResponse.ok(subjectService.listSubjects());
    }

    @PostMapping
    public ApiResponse<SubjectView> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(subjectService.createSubject(request));
    }

    @GetMapping("/{subjectId}")
    public ApiResponse<SubjectView> subject(@PathVariable Long subjectId) {
        return ApiResponse.ok(subjectService.getSubject(subjectId));
    }

    @PatchMapping("/{subjectId}")
    public ApiResponse<SubjectView> updateSubject(
            @PathVariable Long subjectId,
            @Valid @RequestBody UpdateSubjectRequest request
    ) {
        return ApiResponse.ok(subjectService.updateSubject(subjectId, request));
    }

    @DeleteMapping("/{subjectId}")
    public ApiResponse<Void> deleteSubject(@PathVariable Long subjectId) {
        subjectService.deleteSubject(subjectId);
        return ApiResponse.ok();
    }

    @PostMapping("/{subjectId}/bilibili-binding")
    public ApiResponse<SubjectBilibiliBindingView> createBinding(
            @PathVariable Long subjectId,
            @Valid @RequestBody UpdateSubjectBilibiliBindingRequest request
    ) {
        return ApiResponse.ok(subjectService.bindSubject(subjectId, request));
    }

    @PatchMapping("/{subjectId}/bilibili-binding")
    public ApiResponse<SubjectBilibiliBindingView> updateBinding(
            @PathVariable Long subjectId,
            @Valid @RequestBody UpdateSubjectBilibiliBindingRequest request
    ) {
        return ApiResponse.ok(subjectService.bindSubject(subjectId, request));
    }

    @GetMapping("/{subjectId}/workbench")
    public ApiResponse<SubjectWorkbenchView> workbench(@PathVariable Long subjectId) {
        return ApiResponse.ok(workbenchService.workbench(subjectId));
    }

    @GetMapping("/{subjectId}/trends")
    public ApiResponse<SubjectTrendView> trends(
            @PathVariable Long subjectId,
            @RequestParam(defaultValue = "follower,live_online") String metrics,
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(defaultValue = "5m") String bucket
    ) {
        return ApiResponse.ok(workbenchService.trends(subjectId, metrics, range, bucket));
    }

    @PutMapping("/{subjectId}/layout")
    public ApiResponse<List<SubjectWidgetLayoutView>> updateLayout(
            @PathVariable Long subjectId,
            @Valid @RequestBody List<SubjectLayoutItemRequest> items
    ) {
        return ApiResponse.ok(subjectService.updateLayout(subjectId, items));
    }
}
