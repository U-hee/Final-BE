package com.final_team4.finalbe.setting.dto.uploadChannel;

import com.final_team4.finalbe.setting.domain.uploadChannel.Channel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadChannelUpdateRequestDto {
    private Long userId;
    private Channel name;
    private String apiKey;
    private String clientId;
    private String clientPw;
    private String blogId;
    private Boolean status;

}
