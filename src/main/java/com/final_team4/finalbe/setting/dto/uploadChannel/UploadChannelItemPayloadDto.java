package com.final_team4.finalbe.setting.dto.uploadChannel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.final_team4.finalbe.setting.domain.uploadChannel.Channel;
import com.final_team4.finalbe.setting.domain.uploadChannel.UploadChannel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UploadChannelItemPayloadDto {
    private Long id;
    private Long userId;
    private Channel name;
    private String apiKey;
    private String clientId;
    private String clientPw;
    private String blogId;
    private Boolean status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "Asia/Seoul")
    private LocalDateTime updatedAt;

    public static UploadChannelItemPayloadDto from(UploadChannel channel) {
        return UploadChannelItemPayloadDto.builder()
                .id(channel.getId())
                .userId(channel.getUserId())
                .name(channel.getName())
                .apiKey(channel.getApiKey())
                .clientId(channel.getClientId())
                .clientPw(channel.getClientPw())
                .blogId(channel.getBlogId())
                .status(channel.getStatus())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }

    public UploadChannel toEntity() {
        return UploadChannel.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .apiKey(apiKey)
                .clientId(clientId)
                .clientPw(clientPw)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
