package com.final_team4.finalbe.setting.dto.uploadChannel;

import com.final_team4.finalbe.setting.domain.uploadChannel.Channel;
import com.final_team4.finalbe.setting.domain.uploadChannel.UploadChannel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadChannelCreateRequestDto {
    private Channel name;
    private String apiKey;
    private String clientId;
    private String clientPw;
    private String blogId;
    private Boolean status;

    public UploadChannel toEntity(Long userId) {
        return UploadChannel.builder()
                .userId(userId)
                .name(name)
                .apiKey(apiKey)
                .clientId(clientId)
                .clientPw(clientPw)
                .blogId(blogId)
                .status(status)
                .build();
    }
}
