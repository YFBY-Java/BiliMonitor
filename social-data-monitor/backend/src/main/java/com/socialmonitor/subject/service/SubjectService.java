package com.socialmonitor.subject.service;

import com.socialmonitor.bilibili.domain.BilibiliMonitoredUser;
import com.socialmonitor.bilibili.dto.AddBilibiliMonitorUserRequest;
import com.socialmonitor.bilibili.dto.BilibiliMonitorUserView;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.dto.AddBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveRoomView;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.bilibili.live.service.BilibiliLiveMonitorService;
import com.socialmonitor.bilibili.repository.BilibiliFollowerMonitorRepository;
import com.socialmonitor.bilibili.service.BilibiliFollowerMonitorService;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.subject.domain.MonitoredSubject;
import com.socialmonitor.subject.domain.SubjectBilibiliBinding;
import com.socialmonitor.subject.domain.SubjectWidgetLayout;
import com.socialmonitor.subject.dto.CreateSubjectRequest;
import com.socialmonitor.subject.dto.SubjectBilibiliBindingView;
import com.socialmonitor.subject.dto.SubjectLayoutItemRequest;
import com.socialmonitor.subject.dto.SubjectView;
import com.socialmonitor.subject.dto.SubjectWidgetLayoutView;
import com.socialmonitor.subject.dto.UpdateSubjectBilibiliBindingRequest;
import com.socialmonitor.subject.dto.UpdateSubjectRequest;
import com.socialmonitor.subject.repository.SubjectRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.subject-monitor", name = "enabled", matchIfMissing = true)
public class SubjectService {

    private static final Logger log = LoggerFactory.getLogger(SubjectService.class);

    private final SubjectRepository repository;
    private final BilibiliFollowerMonitorRepository followerRepository;
    private final BilibiliLiveMonitorRepository liveRepository;
    private final BilibiliFollowerMonitorService followerMonitorService;
    private final BilibiliLiveMonitorService liveMonitorService;

    public SubjectService(
            SubjectRepository repository,
            BilibiliFollowerMonitorRepository followerRepository,
            BilibiliLiveMonitorRepository liveRepository,
            BilibiliFollowerMonitorService followerMonitorService,
            BilibiliLiveMonitorService liveMonitorService
    ) {
        this.repository = repository;
        this.followerRepository = followerRepository;
        this.liveRepository = liveRepository;
        this.followerMonitorService = followerMonitorService;
        this.liveMonitorService = liveMonitorService;
    }

    public List<SubjectView> listSubjects() {
        return repository.findAllSubjects().stream()
                .map(subject -> {
                    SubjectBilibiliBinding binding = repository.findBinding(subject.id()).orElse(null);
                    return toSubjectView(maybeBackfillSubjectProfile(subject, binding), binding);
                })
                .toList();
    }

    public SubjectView getSubject(Long subjectId) {
        MonitoredSubject subject = requireSubject(subjectId);
        SubjectBilibiliBinding binding = repository.findBinding(subject.id()).orElse(null);
        return toSubjectView(maybeBackfillSubjectProfile(subject, binding), binding);
    }

    @Transactional
    public SubjectView createSubject(CreateSubjectRequest request) {
        BindingInput resolved = hasBindingPayload(
                request.bilibiliUserMonitorId(),
                request.bilibiliLiveRoomMonitorId(),
                request.mid(),
                request.roomId(),
                request.danmuEnabled()
        ) ? resolveBindingInput(new UpdateSubjectBilibiliBindingRequest(
                request.bilibiliUserMonitorId(),
                request.bilibiliLiveRoomMonitorId(),
                request.mid(),
                request.roomId(),
                null,
                request.danmuEnabled()
        )) : null;
        String displayName = resolveSubjectDisplayName(request.displayName(), resolved);
        String avatarUrl = resolveSubjectAvatarUrl(request.avatarUrl(), resolved);
        MonitoredSubject subject = repository.insertSubject(
                displayName,
                avatarUrl,
                blankToNull(request.remark()),
                normalizeTags(request.tags())
        );
        upsertDefaultLayouts(subject.id());
        if (resolved != null) {
            repository.upsertBinding(
                    subject.id(),
                    resolved.userMonitorId(),
                    resolved.liveRoomMonitorId(),
                    resolved.mid(),
                    resolved.roomId(),
                    null,
                    request.danmuEnabled()
            );
            subject = maybeBackfillSubjectProfile(subject);
        }
        return getSubject(subject.id());
    }

    public SubjectView updateSubject(Long subjectId, UpdateSubjectRequest request) {
        requireSubject(subjectId);
        String status = request.enabled() == null ? null : request.enabled() ? "ACTIVE" : "PAUSED";
        MonitoredSubject updated = repository.updateSubject(
                subjectId,
                request.displayName() == null ? null : normalizeDisplayName(request.displayName()),
                blankToNull(request.avatarUrl()),
                blankToNull(request.remark()),
                request.tags() == null ? null : normalizeTags(request.tags()),
                status
        );
        return toSubjectView(updated, repository.findBinding(updated.id()).orElse(null));
    }

    public void deleteSubject(Long subjectId) {
        requireSubject(subjectId);
        repository.deleteSubject(subjectId);
    }

    public SubjectBilibiliBindingView bindSubject(Long subjectId, UpdateSubjectBilibiliBindingRequest request) {
        requireSubject(subjectId);
        BindingInput resolved = resolveBindingInput(request);
        SubjectBilibiliBinding binding = repository.upsertBinding(
                subjectId,
                resolved.userMonitorId(),
                resolved.liveRoomMonitorId(),
                resolved.mid(),
                resolved.roomId(),
                request.enabledCapabilities(),
                request.danmuEnabled()
        );
        return toBindingView(binding);
    }

    public List<SubjectWidgetLayoutView> updateLayout(Long subjectId, List<SubjectLayoutItemRequest> items) {
        requireSubject(subjectId);
        repository.deleteLayouts(subjectId);
        if (items == null || items.isEmpty()) {
            upsertDefaultLayouts(subjectId);
        } else {
            for (SubjectLayoutItemRequest item : items) {
                repository.upsertLayout(
                        subjectId,
                        item.widgetKey(),
                        item.enabled(),
                        item.position(),
                        item.settings()
                );
            }
        }
        return repository.findAllLayouts(subjectId).stream().map(this::toLayoutView).toList();
    }

    public MonitoredSubject requireSubject(Long subjectId) {
        return repository.findSubject(subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户监控对象不存在：" + subjectId));
    }

    public SubjectView toSubjectView(MonitoredSubject subject, SubjectBilibiliBinding binding) {
        return new SubjectView(
                subject.id(),
                subject.displayName(),
                subject.avatarUrl(),
                subject.remark(),
                subject.tags(),
                subject.monitorStatus(),
                subject.healthScore(),
                subject.lastSuccessAt(),
                subject.lastEventAt(),
                subject.createdAt(),
                subject.updatedAt(),
                binding == null ? null : toBindingView(binding)
        );
    }

    public SubjectBilibiliBindingView toBindingView(SubjectBilibiliBinding binding) {
        return new SubjectBilibiliBindingView(
                binding.id(),
                binding.subjectId(),
                binding.bilibiliUserMonitorId(),
                binding.bilibiliLiveRoomMonitorId(),
                binding.mid(),
                binding.roomId(),
                binding.enabledCapabilities(),
                binding.danmuEnabled(),
                binding.createdAt(),
                binding.updatedAt()
        );
    }

    public SubjectWidgetLayoutView toLayoutView(SubjectWidgetLayout layout) {
        return new SubjectWidgetLayoutView(
                layout.widgetKey(),
                layout.enabled(),
                layout.position(),
                layout.settings()
        );
    }

    private void upsertDefaultLayouts(Long subjectId) {
        repository.upsertLayout(subjectId, "bilibili-follower-live-heat", true, position(0, 0, 8, 3), Map.of());
        repository.upsertLayout(subjectId, "bilibili-live-danmu", true, position(8, 0, 4, 3), Map.of());
        repository.upsertLayout(subjectId, "subject-health-events", true, position(0, 3, 8, 2), Map.of());
        repository.upsertLayout(subjectId, "widget-placeholder", true, position(8, 3, 4, 2), Map.of());
    }

    private MonitoredSubject maybeBackfillSubjectProfile(MonitoredSubject subject) {
        SubjectBilibiliBinding binding = repository.findBinding(subject.id()).orElse(null);
        return maybeBackfillSubjectProfile(subject, binding);
    }

    private MonitoredSubject maybeBackfillSubjectProfile(MonitoredSubject subject, SubjectBilibiliBinding binding) {
        if (binding == null || (subject.avatarUrl() != null && !subject.avatarUrl().isBlank())) {
            return subject;
        }
        String avatarUrl = null;
        String displayName = subject.displayName();
        if (binding.bilibiliUserMonitorId() != null) {
            BilibiliMonitoredUser user = followerRepository.findById(binding.bilibiliUserMonitorId()).orElse(null);
            if (user != null) {
                avatarUrl = user.avatarUrl();
                if (displayName == null || displayName.isBlank()) {
                    displayName = user.nickname();
                }
            }
        }
        if (avatarUrl == null && binding.bilibiliLiveRoomMonitorId() != null) {
            BilibiliLiveRoomMonitor room = liveRepository.findById(binding.bilibiliLiveRoomMonitorId()).orElse(null);
            if (room != null) {
                avatarUrl = room.faceUrl();
                if (displayName == null || displayName.isBlank()) {
                    displayName = room.uname();
                }
            }
        }
        if (avatarUrl == null) {
            return subject;
        }
        return repository.updateSubject(subject.id(), displayName, avatarUrl, subject.remark(), subject.tags(), null);
    }

    private BindingInput resolveBindingInput(UpdateSubjectBilibiliBindingRequest request) {
        Long userMonitorId = request.bilibiliUserMonitorId();
        Long liveRoomMonitorId = request.bilibiliLiveRoomMonitorId();
        Long mid = request.mid();
        Long roomId = request.roomId();

        BilibiliMonitoredUser user = null;
        if (userMonitorId != null) {
            Long requestedUserMonitorId = userMonitorId;
            user = followerRepository.findById(userMonitorId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "B站粉丝监控用户不存在：" + requestedUserMonitorId));
        } else if (mid != null) {
            user = followerRepository.findByMid(mid).orElse(null);
            if (user == null) {
                BilibiliMonitorUserView created = followerMonitorService.addUser(new AddBilibiliMonitorUserRequest(mid, null));
                user = followerRepository.findById(created.id()).orElse(null);
            }
        }
        if (user != null) {
            userMonitorId = user.id();
            mid = user.mid();
        }

        BilibiliLiveRoomMonitor room = null;
        if (liveRoomMonitorId != null) {
            Long requestedLiveRoomMonitorId = liveRoomMonitorId;
            room = liveRepository.findById(liveRoomMonitorId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "B站直播间监控不存在：" + requestedLiveRoomMonitorId));
        } else if (roomId != null) {
            room = liveRepository.findByRoomId(roomId).orElse(null);
            if (room == null) {
                BilibiliLiveRoomView created = liveMonitorService.addRoom(new AddBilibiliLiveRoomMonitorRequest(null, roomId, null));
                room = liveRepository.findById(created.id()).orElse(null);
            }
        } else if (mid != null) {
            room = liveRepository.findByUid(mid).orElse(null);
            if (room == null) {
                try {
                    BilibiliLiveRoomView created = liveMonitorService.addRoom(new AddBilibiliLiveRoomMonitorRequest(mid, null, null));
                    room = liveRepository.findById(created.id()).orElse(null);
                } catch (BusinessException exception) {
                    log.warn("Unable to auto-create Bilibili live monitor for subject. mid={}, message={}",
                            mid, exception.getMessage());
                }
            }
        }
        if (room != null) {
            liveRoomMonitorId = room.id();
            roomId = room.roomId();
            if (mid == null) {
                mid = room.uid();
            }
        }
        if (userMonitorId == null && mid != null) {
            user = followerRepository.findByMid(mid).orElse(null);
            if (user == null) {
                BilibiliMonitorUserView created = followerMonitorService.addUser(new AddBilibiliMonitorUserRequest(mid, null));
                user = followerRepository.findById(created.id()).orElse(null);
            }
            if (user != null) {
                userMonitorId = user.id();
                mid = user.mid();
            }
        }
        return new BindingInput(userMonitorId, liveRoomMonitorId, mid, roomId);
    }

    private String resolveSubjectDisplayName(String requestedDisplayName, BindingInput binding) {
        String displayName = blankToNull(requestedDisplayName);
        if (displayName != null) {
            return displayName;
        }
        if (binding != null && binding.userMonitorId() != null) {
            BilibiliMonitoredUser user = followerRepository.findById(binding.userMonitorId()).orElse(null);
            if (user != null && blankToNull(user.nickname()) != null) {
                return user.nickname();
            }
        }
        if (binding != null && binding.liveRoomMonitorId() != null) {
            BilibiliLiveRoomMonitor room = liveRepository.findById(binding.liveRoomMonitorId()).orElse(null);
            if (room != null && blankToNull(room.uname()) != null) {
                return room.uname();
            }
        }
        if (binding != null && binding.mid() != null) {
            return "Bilibili UID " + binding.mid();
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Subject name cannot be empty unless a Bilibili UID is provided.");
    }

    private String resolveSubjectAvatarUrl(String requestedAvatarUrl, BindingInput binding) {
        String avatarUrl = blankToNull(requestedAvatarUrl);
        if (avatarUrl != null || binding == null) {
            return avatarUrl;
        }
        if (binding.userMonitorId() != null) {
            BilibiliMonitoredUser user = followerRepository.findById(binding.userMonitorId()).orElse(null);
            if (user != null && blankToNull(user.avatarUrl()) != null) {
                return user.avatarUrl();
            }
        }
        if (binding.liveRoomMonitorId() != null) {
            BilibiliLiveRoomMonitor room = liveRepository.findById(binding.liveRoomMonitorId()).orElse(null);
            if (room != null && blankToNull(room.faceUrl()) != null) {
                return room.faceUrl();
            }
        }
        return null;
    }

    private boolean hasBindingPayload(Long userMonitorId, Long liveRoomMonitorId, Long mid, Long roomId, Boolean danmuEnabled) {
        return userMonitorId != null || liveRoomMonitorId != null || mid != null || roomId != null || danmuEnabled != null;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = blankToNull(displayName);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户监控名称不能为空。");
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(this::blankToNull)
                .filter(tag -> tag != null)
                .distinct()
                .limit(12)
                .toList();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> position(int x, int y, int w, int h) {
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", x);
        position.put("y", y);
        position.put("w", w);
        position.put("h", h);
        return position;
    }

    private record BindingInput(Long userMonitorId, Long liveRoomMonitorId, Long mid, Long roomId) {
    }
}
