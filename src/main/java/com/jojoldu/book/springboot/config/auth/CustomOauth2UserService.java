package com.jojoldu.book.springboot.config.auth;

import com.jojoldu.book.springboot.config.auth.dto.OAuthAttributes;
import com.jojoldu.book.springboot.config.auth.dto.SessionUser;
import com.jojoldu.book.springboot.domain.user.User;
import com.jojoldu.book.springboot.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Collections;

@RequiredArgsConstructor
@Service
public class CustomOauth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    /**
     * OAuth2UserService 인터페이스를 구현한 커스텀 서비스.
     * OAuth2UserRequest → OAuth2 인증 요청에 대한 정보 (클라이언트, 토큰 등).
     * OAuth2User → OAuth2 제공자(구글, 네이버 등)로부터 받아온 사용자 정보.
     */
    private final UserRepository userRepository;
    private final HttpSession httpSession;  //로그인한 사용자 정보를 세션에 저장해서, 어디서든 꺼내 쓸 수 있도록 함.

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();    //현재 로그인 서비스 구분 //네이버, 구글..
        //OAuth2 로그인 시 기본 키(PK) 같은 역할을 하는 값 (ex. Google의 경우 sub, 네이버의 경우 id).
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        //OAuth2UserService 를 통해 가져온 OAuth2User 의 attribute 를 담을 클래스
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        User user = saveOrUpdate(attributes);

        //세션에 SessionUser(Dto)를 저장.
        //엔티티(User)를 직접 저장하지 않는 이유 → 직렬화 문제 방지 + 엔티티는 절대 세션에 두면 안 됨 (엔티티 변경 관리 문제).
        httpSession.setAttribute("user", new SessionUser(user));

        //Spring Security 세션에 저장될 사용자 객체 반환.
        //권한(Role), 사용자 속성(Attribute), 식별자 키를 담음.
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())), attributes.getAttributes(), attributes.getNameAttributeKey());
    }

    /**
     * 이메일을 기준으로 기존 유저 찾음.
     * 있으면 → update() 실행 (이름, 프로필 사진 업데이트).
     * 없으면 → 새로운 유저 생성 (toEntity()).
     * 마지막에 저장 후 반환.
     */
    private User saveOrUpdate(OAuthAttributes attributes) {
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> entity.update(attributes.getName(), attributes.getPicture()))
                .orElse(attributes.toEntity());

        return userRepository.save(user);
    }
}
