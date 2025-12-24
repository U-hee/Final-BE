package com.final_team4.finalbe.user.service;

import com.final_team4.finalbe._core.exception.ContentNotFoundException;
import com.final_team4.finalbe._core.exception.DuplicateEmailException;
import com.final_team4.finalbe._core.exception.UnauthorizedException;
import com.final_team4.finalbe.content.domain.ContentGenType;
import com.final_team4.finalbe.schedule.domain.RepeatInterval;
import com.final_team4.finalbe.schedule.dto.schedule.ScheduleCreateRequestDto;
import com.final_team4.finalbe.schedule.dto.scheduleSetting.ScheduleSettingCreateRequestDto;
import com.final_team4.finalbe.schedule.service.ScheduleService;
import com.final_team4.finalbe.schedule.service.ScheduleSettingService;
import com.final_team4.finalbe.setting.domain.uploadChannel.Channel;
import com.final_team4.finalbe.setting.dto.llm.LlmChannelCreateRequestDto;
import com.final_team4.finalbe.setting.dto.notification.NotificationCredentialCreateRequestDto;
import com.final_team4.finalbe.setting.dto.uploadChannel.UploadChannelCreateRequestDto;
import com.final_team4.finalbe.setting.service.llm.LlmChannelService;
import com.final_team4.finalbe.setting.service.notification.NotificationCredentialService;
import com.final_team4.finalbe.setting.service.uploadChannel.UploadChannelService;
import com.final_team4.finalbe.user.domain.User;
import com.final_team4.finalbe.user.dto.PasswordUpdateRequest;
import com.final_team4.finalbe.user.dto.UserRegisterRequestDto;
import com.final_team4.finalbe.user.dto.UserUpdateRequest;
import com.final_team4.finalbe.user.dto.response.UserFullResponse;
import com.final_team4.finalbe.user.dto.response.UserSummaryResponse;
import com.final_team4.finalbe.user.mapper.UserMapper;
import com.final_team4.finalbe.user.mapper.UserInfoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.final_team4.finalbe._core.exception.BadRequestException;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
public class UserService {

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;
    private final ScheduleSettingService scheduleSettingService;
    private final LlmChannelService llmChannelService;
    private final ScheduleService scheduleService;
    private final NotificationCredentialService notificationCredentialService;
    private final UploadChannelService uploadChannelService;

    @Transactional
    public UserSummaryResponse register(@Valid UserRegisterRequestDto request) {
        ensureEmailAvailable(request.getEmail());
        String encoded = passwordEncoder.encode(request.getPassword());

        User user = request.toEntity().toBuilder()
                .password(encoded)
                .build();
        userMapper.insert(user);


        ScheduleSettingCreateRequestDto defaultSetting = ScheduleSettingCreateRequestDto.builder()
                .isRun(false)
                .maxDailyRuns(0L)
                .retryOnFail(0L)
                .build();
        scheduleSettingService.create(user.getId(), defaultSetting);
        //스케쥴 세팅 기본값 채워주기

        LlmChannelCreateRequestDto defaultLlmSetting = LlmChannelCreateRequestDto.builder()
                .name("openAi")
                .modelName("gpt-5")
                .apiKey("")
                .status(false)
                .maxTokens(2000)
                .temperature(BigDecimal.valueOf(0.9))
                .prompt("")
                .generationType(ContentGenType.AUTO)
                .build();
        llmChannelService.create(user.getId(), defaultLlmSetting);
        //llm 기본값 채워주기

        ScheduleCreateRequestDto scheduleCreateRequestDto = ScheduleCreateRequestDto.builder()
                .title("기본 설정")
                .repeatInterval(RepeatInterval.DAILY)
                .startTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(8, 0)))
                .build();
        scheduleService.insert(user.getId(), scheduleCreateRequestDto);
        //스케줄 기본값 채워주기

        NotificationCredentialCreateRequestDto notificationCredentialCreateRequestDto = NotificationCredentialCreateRequestDto.builder()
                .channelId(1L)
                .webhookUrl("")
                .apiToken("")
                .isActive(false)
                .build();
        notificationCredentialService.insert(user.getId(), notificationCredentialCreateRequestDto);
        //알림 설정 기본값 채워주기


        var uploadChannelDto = UploadChannelCreateRequestDto.builder()
                .name(Channel.NAVER)
                .apiKey("")
                .status(true)
                .clientId("")
                .clientPw("")
                .blogId("")
                .build();
        uploadChannelService.createChannel(user.getId(), uploadChannelDto);
        // 업로드 채널 기본값 채워주기
        return userInfoMapper.toUserSummary(user);
    }

    private void ensureEmailAvailable(String email) {
        if (userMapper.findByEmail(email) != null) {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다 : " + email);
        }
    }

    @Transactional(readOnly = true)
    public UserFullResponse findProfile(Long userId) {
        User user = Optional.ofNullable(userMapper.findAvailableById(userId))
                .orElseThrow(()->new ContentNotFoundException("사용자를 찾을 수 없습니다."));
                return userInfoMapper.toUserFull(user);
    }


    @Transactional
    public User updateProfile(Long userId, UserUpdateRequest request ) {

        int updated = userMapper.updateProfile(userId,request.getName());
        if(updated == 0){
            throw new ContentNotFoundException("업데이트를 실패했습니다.");
        }

        return Optional.ofNullable(userMapper.findAvailableById(userId))
                .orElseThrow(()->new ContentNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void updatePassword(PasswordUpdateRequest request, Long  userId) {
        User user = Optional.ofNullable(userMapper.findAvailableById(userId))
                .orElseThrow(()-> new ContentNotFoundException("사용자를 찾을 수 없습니다."));

        //비밀번호 인증 순서 -> 기존 비밀번호가 일치하는지부터 확인해야 보안에 더 좋음
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new UnauthorizedException("비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new BadRequestException("새 비밀번호와 비밀번호 확인이 일치하지 않습니다");
        }

        String encoded = passwordEncoder.encode(request.getNewPassword());

        int updated = userMapper.updatePassword(user.getId(),encoded);
        if(updated == 0){
            throw new ContentNotFoundException("비밀번호를 저장하지 못했습니다.");
        }


    }
}
