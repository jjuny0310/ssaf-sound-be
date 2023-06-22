package com.ssafy.ssafsound.domain.recruit.service;

import com.ssafy.ssafsound.domain.auth.dto.AuthenticatedMember;
import com.ssafy.ssafsound.domain.member.domain.AuthenticationStatus;
import com.ssafy.ssafsound.domain.member.domain.MajorType;
import com.ssafy.ssafsound.domain.member.domain.Member;
import com.ssafy.ssafsound.domain.member.repository.MemberRepository;
import com.ssafy.ssafsound.domain.meta.domain.*;
import com.ssafy.ssafsound.domain.meta.service.MetaDataConsumer;
import com.ssafy.ssafsound.domain.recruit.domain.*;
import com.ssafy.ssafsound.domain.recruit.dto.GetRecruitDetailResDto;
import com.ssafy.ssafsound.domain.recruit.dto.PostRecruitReqDto;
import com.ssafy.ssafsound.domain.recruit.dto.RecruitLimitElement;
import com.ssafy.ssafsound.domain.recruit.exception.RecruitException;
import com.ssafy.ssafsound.domain.recruitapplication.repository.RecruitApplicationRepository;
import com.ssafy.ssafsound.domain.recruit.repository.RecruitLimitationRepository;
import com.ssafy.ssafsound.domain.recruit.repository.RecruitRepository;
import com.ssafy.ssafsound.domain.recruit.repository.RecruitScrapRepository;
import com.ssafy.ssafsound.domain.recruitapplication.domain.RecruitApplication;
import com.ssafy.ssafsound.global.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class RecruitServiceTest {

    @Mock
    private RecruitRepository recruitRepository;

    @Mock
    private RecruitLimitationRepository recruitLimitationRepository;

    @Mock
    private RecruitApplicationRepository recruitApplicationRepository;

    @Mock
    private RecruitScrapRepository recruitScrapRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MetaDataConsumer metaDataConsumer;

    @InjectMocks
    private RecruitService recruitService;

    private Member member = Member.builder()
            .id(1L)
            .nickname("khs")
            .semester(9)
            .ssafyMember(true)
            .certificationState(AuthenticationStatus.CERTIFIED)
            .major(true)
            .majorType(MajorType.builder().build())
            .campus(new MetaData(Campus.SEOUL))
            .build();

    private Recruit recruit = Recruit.builder()
            .id(1L)
            .build();

    private Recruit savedRecruit = Recruit.builder()
            .id(2L)
            .view(0L)
            .member(member)
            .deletedRecruit(false)
            .startDateTime(LocalDate.now().atStartOfDay())
            .endDateTime(LocalDate.now().plusDays(3).atTime(LocalTime.MAX))
            .build();

    private List<RecruitLimitation> limits = List.of(RecruitLimitation.builder()
            .recruit(savedRecruit)
            .limitation(2)
            .currentNumber(0)
            .type(new MetaData(RecruitType.DESIGN))
            .build());

    private List<RecruitSkill> skills = List.of(RecruitSkill.builder()
            .recruit(savedRecruit)
            .id(1L)
            .recruit(savedRecruit)
            .skill(new MetaData(RecruitType.BACK_END))
            .build());

    private RecruitScrap recruitScrap = RecruitScrap.builder().member(member).recruit(recruit).build();

    private final PostRecruitReqDto postRecruitReqDto = new PostRecruitReqDto(
            Category.STUDY.name(), LocalDate.now(),  "스터디/프로젝트 모집 제목", "컨텐츠",
            RecruitType.DESIGN.getName(), Arrays.stream(Skill.values()).map(Skill::getName).collect(Collectors.toList()),
            Collections.singletonList("프로젝트/스터디 등록자가 참여자에게 묻고 싶은 자유 질문"),
            Arrays.stream(RecruitType.values()).map(recruitType-> new RecruitLimitElement(recruitType.getName(), 2)).collect(Collectors.toList()));

    @BeforeEach
    void setStub() {
        savedRecruit.setRecruitLimitations(limits);
        savedRecruit.setRecruitSkill(skills);

        // Repository Mocking
        Mockito.lenient().when(memberRepository.findById(1L)).thenReturn(java.util.Optional.ofNullable(member));
        Mockito.lenient().when(memberRepository.findById(2L)).thenThrow(new RuntimeException());

        Mockito.lenient().when(recruitScrapRepository.findByRecruitIdAndMemberId(1L, 1L)).thenReturn(java.util.Optional.ofNullable(recruitScrap));

        Mockito.lenient().when(recruitRepository.findByIdUsingFetchJoinRegisterAndRecruitLimitation(2L))
                .thenReturn(java.util.Optional.ofNullable(savedRecruit));

        // MetaData Consumer Mocking
        Arrays.stream(RecruitType.values()).forEach(recruitType -> Mockito.lenient()
                .when(metaDataConsumer.getMetaData(MetaDataType.RECRUIT_TYPE.name(), recruitType.getName()))
                .thenReturn(new MetaData(recruitType)));
    }

    @DisplayName("토큰과 정상 리크루트글 등록 요청이 넘어온 경우 리크루트 글 등록 성공")
    @Test
    void Given_AuthenticatedMemberAndPostRecruitReqDto_When_SaveRecruitService_Then_Success() {
        AuthenticatedMember existUser = AuthenticatedMember.builder()
                .memberId(1L)
                .build();

        Recruit recruit = recruitService.saveRecruit(existUser, postRecruitReqDto);

        assertAll(
                ()-> assertEquals(member, recruit.getMember()),
                ()-> {
                    RecruitApplication registerApplication = recruit.getApplications().get(0);
                    assertNotNull(registerApplication);
                    assertEquals( 1, recruit.getApplications().size());
                    assertEquals(RecruitType.DESIGN.getName(), registerApplication.getType().getName());
                    assertEquals(1, registerApplication.getMember().getId());
                },
                ()-> {
                    List<RecruitLimitation> recruitLimitations = recruit.getLimitations();
                    assertNotNull(recruitLimitations);
                    assertEquals(RecruitType.values().length, recruitLimitations.size());

                    for(RecruitLimitation recruitLimitation: recruitLimitations) {
                        assertEquals(2, recruitLimitation.getLimitation());
                        if(recruitLimitation.getType().getName().equals(RecruitType.DESIGN.getName())) {
                            assertEquals(1, recruitLimitation.getCurrentNumber());
                        } else {
                            assertEquals(0, recruitLimitation.getCurrentNumber());
                        }
                    }
                }
        );
    }

    @DisplayName("토큰으로부터 얻은 사용자 정보가 유효하지 않은 경우 리크루트글 등록 실패")
    @Test
    void Given_EmptyAuthenticatedMemberAndPostRecruitReqDto_When_SaveRecruitService_Then_ThrowException() {
        AuthenticatedMember notExistMember = AuthenticatedMember.builder()
                .memberId(2L)
                .build();

        assertThrows(RuntimeException.class, ()-> recruitService.saveRecruit(notExistMember, postRecruitReqDto));
    }

    @DisplayName("사용자 리크루팅 스크랩 등록")
    @Test
    void Given_MemberIdAndRecruitId_When_TryToggleRecruitScrap_Then_InsertRecruitScrap() {
        Long recruitId = 2L, memberId = 1L;
        recruitService.toggleRecruitScrap(recruitId, memberId);
        assertFalse(recruitService.toggleRecruitScrap(recruitId, memberId));
    }

    @DisplayName("사용자 리크루팅 스크랩 취소(토글)")
    @Test
    void Given_MemberIdAndRecruitId_When_TryToggleRecruitScrap_Then_DeleteRecruitScrap() {
        Long recruitId = 1L, memberId = 1L;
        assertTrue(recruitService.toggleRecruitScrap(recruitId, memberId));
    }

    @DisplayName("리크루트 상세 조회")
    @Test
    void Given_RecruitId_When_GetRecruitDetail_Then_Success() {
        GetRecruitDetailResDto dto = recruitService.getRecruitDetail(2L);

        assertAll(
                ()->assertEquals(1L, dto.getView()),
                ()->assertEquals(false, dto.isFinishedRecruit())
        );
    }

    @DisplayName("존재하지 않는 리크루트에 대한 상세 조회 실패")
    @Test
    void Given_NotExistRecruitId_When_GetRecruitDetail_Then_Fail() {
        assertThrows(ResourceNotFoundException.class, ()->recruitService.getRecruitDetail(1L));
    }


    @DisplayName("삭제된 리크루트 상세 조회시 null값 return")
    @Test
    void Given_DeletedRecruitId_When_GetRecruitDetail_Then_Fail() {
        savedRecruit.delete();
        assertThrows(RecruitException.class, ()->recruitService.getRecruitDetail(2L));
    }
}