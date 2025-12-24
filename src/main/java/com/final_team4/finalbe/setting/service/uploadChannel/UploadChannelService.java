package com.final_team4.finalbe.setting.service.uploadChannel;

import com.final_team4.finalbe._core.exception.ContentNotFoundException;
import com.final_team4.finalbe.setting.domain.uploadChannel.UploadChannel;
import com.final_team4.finalbe.setting.dto.uploadChannel.*;
import com.final_team4.finalbe.setting.mapper.uploadChannel.UploadChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadChannelService {

    private final UploadChannelMapper uploadChannelMapper;

    public List<UploadChannelItemPayloadDto> getChannelsByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("해당 유저 아이디를 찾을 수 없습니다.");
        }

        List<UploadChannel> channels = uploadChannelMapper.findByUserId(userId);

        if (channels.isEmpty()) {
            throw new ContentNotFoundException("해당 유저의 업로드 채널을 찾을 수 없습니다.");
        }

        return channels.stream()
                .map(UploadChannelItemPayloadDto::from)
                .toList();
    }

    public UploadChannelItemPayloadDto getChannelById(Long userId, Long channelId) {
        UploadChannel entity = uploadChannelMapper.findById(userId, channelId);
        return UploadChannelItemPayloadDto.from(entity);
    }

    public UploadChannelCreateResponseDto createChannel(Long userId, UploadChannelCreateRequestDto requestDto) {
        UploadChannel entity = requestDto.toEntity(userId);
        uploadChannelMapper.insert(entity);
        return UploadChannelCreateResponseDto.from(entity);
    }

    public UploadChannelUpdateResponseDto updateChannel(Long userId, Long channelId, UploadChannelUpdateRequestDto requestDto) {
        UploadChannel entity = uploadChannelMapper.findById(userId, channelId);
        entity.update(
                requestDto.getApiKey(),
                requestDto.getClientId(),
                requestDto.getClientPw(),
                requestDto.getBlogId(),
                requestDto.getStatus()
        );
        uploadChannelMapper.update(entity);
        return UploadChannelUpdateResponseDto.from(entity);
    }

    public UploadChannelItemPayloadDto updateStatus(Long userId, Long channelId) {
        UploadChannel entity = uploadChannelMapper.findById(userId, channelId);
        uploadChannelMapper.updateStatusById(userId, channelId, entity.getStatus() != true);
        return UploadChannelItemPayloadDto.from(entity);
    }

    public UploadChannelItemPayloadDto getActiveChannelById(Long userId) {
        UploadChannel entity = uploadChannelMapper.findActiveByUserId(userId);
        return UploadChannelItemPayloadDto.from(entity);
    }

}
