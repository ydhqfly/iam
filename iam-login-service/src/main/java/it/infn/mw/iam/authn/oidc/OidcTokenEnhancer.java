package it.infn.mw.iam.authn.oidc;

import static java.util.Objects.isNull;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mitre.oauth2.model.ClientDetailsEntity;
import org.mitre.oauth2.model.OAuth2AccessTokenEntity;
import org.mitre.oauth2.service.SystemScopeService;
import org.mitre.openid.connect.model.UserInfo;
import org.mitre.openid.connect.service.OIDCTokenService;
import org.mitre.openid.connect.service.UserInfoService;
import org.mitre.openid.connect.token.ConnectTokenEnhancer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import com.nimbusds.jwt.SignedJWT;
import it.infn.mw.iam.core.IamProperties;
import it.infn.mw.iam.core.IamScopeClaimTranslationService;
import it.infn.mw.iam.core.oauth.scope.IamScopeFilter;
import it.infn.mw.iam.persistence.model.IamGroup;
import it.infn.mw.iam.persistence.model.IamUserInfo;

public class OidcTokenEnhancer extends ConnectTokenEnhancer {

  @Autowired
  private UserInfoService userInfoService;

  @Autowired
  private OIDCTokenService connectTokenService;

  @Autowired
  private IamScopeFilter scopeFilter;

  @Autowired
  private IamScopeClaimTranslationService scopeClaimConverter;

  @Value("${iam.access_token.include_authn_info}")
  private Boolean includeAuthnInfo;

  private static final String AUD_KEY = "aud";

  private static final Set<String> ADDITIONAL_CLAIMS =
      Sets.newHashSet("email", "preferred_username", "organisation_name", "groups");

  private String organisationName = IamProperties.INSTANCE.getOrganisationName();

  private SignedJWT signClaims(JWTClaimsSet claims) {
    JWSAlgorithm signingAlg = getJwtService().getDefaultSigningAlgorithm();

    JWSHeader header = new JWSHeader(signingAlg, null, null, null, null, null, null, null, null,
        null, getJwtService().getDefaultSignerKeyId(), null, null);
    SignedJWT signedJWT = new SignedJWT(header, claims);

    getJwtService().signJwt(signedJWT);
    return signedJWT;

  }

  protected OAuth2AccessTokenEntity buildAccessToken(OAuth2AccessToken accessToken,
      OAuth2Authentication authentication, UserInfo userInfo, Date issueTime) {

    OAuth2AccessTokenEntity token = (OAuth2AccessTokenEntity) accessToken;

    String subject = null;

    if (userInfo == null) {
      subject = authentication.getName();
    } else {
      subject = userInfo.getSub();
    }

    // @formatter:off
    Builder builder = 
        new JWTClaimsSet.Builder()
          .issuer(getConfigBean().getIssuer())
          .issueTime(issueTime)
          .expirationTime(token.getExpiration())
          .subject(subject)
          .jwtID(UUID.randomUUID().toString());
    // @formatter:on

    String audience = (String) authentication.getOAuth2Request().getExtensions().get(AUD_KEY);

    if (!Strings.isNullOrEmpty(audience)) {
      builder.audience(Lists.newArrayList(audience));
    }

    if (includeAuthnInfo && !isNull(userInfo)) {
      Set<String> requiredClaims = scopeClaimConverter.getClaimsForScopeSet(token.getScope());
      requiredClaims.stream().filter(ADDITIONAL_CLAIMS::contains)
          .forEach(c -> builder.claim(c, getClaimValueFromUserInfo(c, (IamUserInfo) userInfo)));
    }

    JWTClaimsSet claims = builder.build();
    token.setJwt(signClaims(claims));

    return token;

  }

  private Object getClaimValueFromUserInfo(String claim, IamUserInfo info) {

    switch (claim) {

      case "email":
        return info.getEmail();

      case "preferred_username":
        return info.getPreferredUsername();

      case "organisation_name":
        return organisationName;

      case "groups":
        List<String> groupNames =
            info.getGroups().stream().map(IamGroup::getName).collect(Collectors.toList());
        return groupNames.toArray(new String[0]);

      default:
        return null;
    }
  }

  @Override
  public OAuth2AccessToken enhance(OAuth2AccessToken accessToken,
      OAuth2Authentication authentication) {

    OAuth2Request originalAuthRequest = authentication.getOAuth2Request();

    String username = authentication.getName();
    String clientId = originalAuthRequest.getClientId();

    UserInfo userInfo = userInfoService.getByUsernameAndClientId(username, clientId);

    scopeFilter.filterScopes(accessToken.getScope(), authentication);

    Date issueTime = new Date();
    OAuth2AccessTokenEntity accessTokenEntity =
        buildAccessToken(accessToken, authentication, userInfo, issueTime);

    /**
     * Authorization request scope MUST include "openid" in OIDC, but access token request may or
     * may not include the scope parameter. As long as the AuthorizationRequest has the proper
     * scope, we can consider this a valid OpenID Connect request. Otherwise, we consider it to be a
     * vanilla OAuth2 request.
     * 
     * Also, there must be a user authentication involved in the request for it to be considered
     * OIDC and not OAuth, so we check for that as well.
     */
    if (originalAuthRequest.getScope().contains(SystemScopeService.OPENID_SCOPE)
        && !authentication.isClientOnly()) {

      ClientDetailsEntity client = getClientService().loadClientByClientId(clientId);

      JWT idToken = connectTokenService.createIdToken(client, originalAuthRequest, issueTime,
          userInfo.getSub(), accessTokenEntity);

      accessTokenEntity.setIdToken(idToken);
    }

    return accessTokenEntity;
  }

}
