package com.final_team4.finalbe.setting.domain.uploadChannel;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadChannel {
    private Long id;
    private Long userId;
    private Channel name;
    private String apiKey;
    private String clientId;
    private String clientPw;
    private String blogId;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void update(String apiKey, String clientId, String clientPw, String blogId, Boolean status) {
        this.apiKey = apiKey;
        this.clientId = clientId;
        this.clientPw = clientPw;
        this.blogId = blogId;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}