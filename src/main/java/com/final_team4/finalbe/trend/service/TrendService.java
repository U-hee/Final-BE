package com.final_team4.finalbe.trend.service;

import com.final_team4.finalbe._core.exception.ContentNotFoundException;
import com.final_team4.finalbe.logger.aop.Loggable;
import com.final_team4.finalbe.logger.domain.type.LogType;
import com.final_team4.finalbe.setting.dto.llm.LlmChannelDetailResponseDto;
import com.final_team4.finalbe.setting.service.llm.LlmChannelService;
import com.final_team4.finalbe.restClient.service.RestClientCallerService;
import com.final_team4.finalbe.trend.domain.Trend;
import com.final_team4.finalbe.trend.domain.TrendSnsType;
import com.final_team4.finalbe.trend.dto.*;
import com.final_team4.finalbe.trend.mapper.TrendMapper;
import com.final_team4.finalbe.setting.dto.uploadChannel.UploadChannelItemPayloadDto;
import com.final_team4.finalbe.setting.service.uploadChannel.UploadChannelService;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TrendService {

    private final TrendMapper trendMapper;
    private final UploadChannelService uploadChannelService;
    private final RestClientCallerService restClientCallerService;
    private final LlmChannelService llmChannelService;

    // 인기검색어 저장(python 호출용)
    @Transactional
    @Loggable(value = "트렌드 데이터 생성 요청", type = LogType.INFO)
    public List<TrendCreateResponseDto> createTrends(List<TrendCreateRequestDto> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        return requests.stream()
                .map(request -> {
                    Trend trend = Trend.builder()
                            .categoryId(request.getCategoryId())
                            .keyword(request.getKeyword())
                            .searchVolume(request.getSearchVolume())
                            .createdAt(LocalDateTime.now())
                            .snsType(request.getSnsType())
                            .build();
                    trendMapper.insert(trend);
                    return TrendCreateResponseDto.from(trend);
                })
                .toList();
    }

    // 인기검색어 목록 조회
    @Loggable(value = "트렌드 목록 조회", type = LogType.INFO)
    public TrendListResponseDto getTrends(int page, int size, TrendSnsType snsType) {
        int offset = page * size;
        List<TrendResponseDto> trends = trendMapper.findAll(size, offset, snsType).stream()
                .map(TrendResponseDto::from)
                .toList();
        long totalCount = trendMapper.countAll(snsType);
        return TrendListResponseDto.of(trends, totalCount, page, size);
    }

    // 인기검색어 컨텐츠 생성 요청(python에 요청)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Loggable(value = "트렌드 콘텐츠 생성 요청", type = LogType.INFO)
    public TrendCreateContentResponseDto requestCreateContent(TrendCreateContentRequestDto request, Long userId) {
        // 유저 업로드 채널 정보 조회
        List<UploadChannelItemPayloadDto> channels = uploadChannelService.getChannelsByUserId(userId);
        if (channels == null || channels.isEmpty()) {
            throw new ContentNotFoundException("등록된 업로드 채널이 없습니다.");
        }

        // LLM 채널 조회
        LlmChannelDetailResponseDto llmChannel = llmChannelService.findByUserId(userId);
        if (llmChannel == null || llmChannel.getGenerationType() == null) {
            throw new ContentNotFoundException("LLM 설정을 찾을 수 없습니다.");
        }

        // UUID 생성
        UUID jobId = UuidCreator.getTimeOrderedEpoch();

        // 파이썬 서비스 호출
        TrendCreateContentPayloadDto payload = TrendCreateContentPayloadDto.of(
                userId,
                request.getKeyword(),
                channels,
                jobId,
                llmChannel
        );
        boolean requested = restClientCallerService.callGeneratePosts(payload);

        return TrendCreateContentResponseDto.of(request.getKeyword(), requested);
    }

}
